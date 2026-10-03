package com.blood.center.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.BloodBag;
import com.blood.center.domain.BloodInventory;
import com.blood.center.domain.CollectionRecord;
import com.blood.center.domain.CrossMatch;
import com.blood.center.domain.HospitalRequest;
import com.blood.center.domain.ScrapRecord;
import com.blood.center.domain.TestSample;
import com.blood.center.dto.BagVO;
import com.blood.center.dto.IssueRequest;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.BloodInventoryRepository;
import com.blood.center.repository.CollectionRecordRepository;
import com.blood.center.repository.CrossMatchRepository;
import com.blood.center.repository.HospitalRequestRepository;
import com.blood.center.repository.PreparationBatchRepository;
import com.blood.center.repository.ScrapRecordRepository;
import com.blood.center.repository.TestSampleRepository;

/**
 * 血浆格（PLASMA 并入冷沉淀 CRYO）统计 / 明细 / 出库一致性集成测试。
 * 用血浆 + 冷沉淀、效期先后交错的数据验证：矩阵袋数 / U 数 / 最近效期 == 格子可用血袋 == 出库可选范围，
 * 出库按最早效期扣减，库存不足不误判，重启重算后矩阵、明细、记录保持一致。
 */
@SpringBootTest
@ActiveProfiles("test")
class PlasmaCryoInventoryIntegrationTest {

    private static final String BT = "A+";

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private TransfusionService transfusionService;
    @Autowired
    private BloodBagRepository bagRepository;
    @Autowired
    private BloodInventoryRepository inventoryRepository;
    @Autowired
    private ScrapRecordRepository scrapRepository;
    @Autowired
    private CrossMatchRepository matchRepository;
    @Autowired
    private HospitalRequestRepository requestRepository;
    @Autowired
    private TestSampleRepository sampleRepository;
    @Autowired
    private CollectionRecordRepository collectionRepository;
    @Autowired
    private PreparationBatchRepository preparationRepository;
    @Autowired
    private TransactionTemplate txTemplate;

    @MockBean
    private StringRedisTemplate redis;
    @MockBean
    private ValueOperations<String, String> valueOps;
    @MockBean
    private ListOperations<String, String> listOps;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        lenient().when(valueOps.increment(anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1L);
        // 发水 AFTER_COMMIT 监听器会写 Redis 列表流水
        lenient().when(redis.opsForList()).thenReturn(listOps);
        lenient().when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        // 按外键依赖顺序清空：配血 → 申请 / 标本 / 报废 → 血袋 → 库存 → 制备批次 → 采血
        matchRepository.deleteAll();
        requestRepository.deleteAll();
        sampleRepository.deleteAll();
        scrapRepository.deleteAll();
        bagRepository.deleteAll();
        inventoryRepository.deleteAll();
        preparationRepository.deleteAll();
        collectionRepository.deleteAll();
    }

    /** 交错效期：血浆远期 2 袋；冷沉淀 3 天到期 1 袋（最早）+ 250 天 1 袋（夹在血浆 200/360 之间） */
    private void seedInterleavedBags() {
        LocalDate today = LocalDate.now();
        saveBag("P-LONG-1", "PLASMA", 220, today.plusDays(360));
        saveBag("P-LONG-2", "PLASMA", 200, today.plusDays(200));
        saveBag("C-NEAR", "CRYO", 30, today.plusDays(3));
        saveBag("C-MID", "CRYO", 200, today.plusDays(250));
        inventoryService.syncInventory(BT, "PLASMA");
    }

    private BloodBag saveBag(String bagNo, String component, int volume, LocalDate expiry) {
        BloodBag bag = new BloodBag();
        bag.setBagNo(bagNo);
        bag.setBloodType(BT);
        bag.setComponent(component);
        bag.setVolumeMl(volume);
        bag.setCollectDate(LocalDate.now().minusDays(1));
        bag.setExpiryDate(expiry);
        bag.setStatus("AVAILABLE");
        bag.setDonorName("测试献血者");
        return bagRepository.save(bag);
    }

    @Test
    @Transactional
    void matrixCellMatchesDrawerBags_afterCryoProduction() {
        seedInterleavedBags();

        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        // 血浆 2 袋（220ml=1U,200ml=1U）+ 冷沉淀 2 袋（30ml 按 1U、200ml=1U）= 4 袋 4 U
        assertThat(inv.getBagCount()).isEqualTo(4);
        assertThat(inv.getUnitCount()).isEqualTo(4);
        // 最近效期 = 冷沉淀 3 天到期那袋，而不是血浆 200 天
        assertThat(inv.getNearestExpiry()).isEqualTo(LocalDate.now().plusDays(3));

        List<BagVO> drawer = inventoryService.cellBags(BT, "PLASMA");
        assertThat(drawer).hasSize(4);
        // FIFO 顺序：冷沉淀 3 天 → 血浆 200 天 → 冷沉淀 250 天 → 血浆 360 天
        assertThat(drawer).extracting(BagVO::bagNo)
                .containsExactly("C-NEAR", "P-LONG-2", "C-MID", "P-LONG-1");
        assertThat(drawer.get(0).daysToExpiry()).isEqualTo(3);
    }

    @Test
    void outboundFifo_earliestExpiryFirstAcrossPlasmaAndCryo() {
        seedInterleavedBags();

        // 连续出 3 袋：应依次扣 冷沉淀(3天) → 血浆(200天) → 冷沉淀(250天)
        for (int i = 0; i < 3; i++) {
            inventoryService.adjust(new com.blood.center.dto.InventoryAdjustRequest(
                    BT, "PLASMA", 1, null, null, "OUT", "库存岗", "手工出库"));
        }

        List<BagVO> remaining = inventoryService.cellBags(BT, "PLASMA");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).bagNo()).isEqualTo("P-LONG-1");   // 360 天的血浆最后出
        assertThat(bagRepository.findByBagNo("C-NEAR").orElseThrow().getStatus()).isEqualTo("SCRAPPED");
        assertThat(bagRepository.findByBagNo("P-LONG-2").orElseThrow().getStatus()).isEqualTo("SCRAPPED");
        assertThat(bagRepository.findByBagNo("C-MID").orElseThrow().getStatus()).isEqualTo("SCRAPPED");

        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(inv.getBagCount()).isEqualTo(1);
        assertThat(inv.getUnitCount()).isEqualTo(1);
        assertThat(inv.getNearestExpiry()).isEqualTo(LocalDate.now().plusDays(360));

        // 每袋一条报废记录，成分保留实际成分（CRYO / PLASMA）
        List<ScrapRecord> scraps = scrapRepository.findAll();
        assertThat(scraps).hasSize(3);
        assertThat(scraps).extracting(ScrapRecord::getBagNo)
                .containsExactlyInAnyOrder("C-NEAR", "P-LONG-2", "C-MID");
    }

    @Test
    void insufficientStockNotMisjudged_andLeavesEverythingUntouched() {
        seedInterleavedBags();

        // 格内 4 袋，强出 5 袋必须报错，且事务回滚：无袋报废、库存仍是 4 袋
        assertThatThrownBy(() -> inventoryService.adjust(
                new com.blood.center.dto.InventoryAdjustRequest(
                        BT, "PLASMA", 5, null, null, "OUT", "库存岗", "手工出库")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可用血袋仅 4 袋");

        assertThat(inventoryService.cellBags(BT, "PLASMA")).hasSize(4);
        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(inv.getBagCount()).isEqualTo(4);
        assertThat(scrapRepository.count()).isZero();
    }

    @Test
    void issueStockCheckUsesExactComponent_notMergedPool() {
        // 只有 1 袋可用冷沉淀（1U）+ 1 袋血浆（1U）
        saveBag("ONLY-CRYO", "CRYO", 200, LocalDate.now().plusDays(30));
        saveBag("ONLY-PLASMA", "PLASMA", 220, LocalDate.now().plusDays(300));
        inventoryService.syncInventory(BT, "PLASMA");

        HospitalRequest request = newRequest("CRYO", 2);
        BloodBag cryoBag = bagRepository.findByBagNo("ONLY-CRYO").orElseThrow();
        releaseSampleForBag(cryoBag);
        CrossMatch match = newMatch(request, cryoBag, 2);

        // 冷沉淀真实只有 1U，申请 2U：不得因为血浆格合并显示 2U 就误判为充足
        assertThatThrownBy(() -> transfusionService.issue(match.getId(), new IssueRequest("姜妍")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存仅 1 U")
                .hasMessageContaining("缺口 1 U");
        assertThat(matchRepository.findById(match.getId()).orElseThrow().getIssued()).isFalse();

        // 改成申请 1U：同一袋 CRYO（标本已放行）可正常出库，血浆格随之重算（剩 1 袋血浆）
        CrossMatch match2 = newMatch(newRequest("CRYO", 1), cryoBag, 1);
        transfusionService.issue(match2.getId(), new IssueRequest("姜妍"));

        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(inv.getBagCount()).isEqualTo(1);
        assertThat(inv.getNearestExpiry()).isEqualTo(LocalDate.now().plusDays(300));
        assertThat(bagRepository.findByBagNo("ONLY-CRYO").orElseThrow().getStatus()).isEqualTo("ISSUED");
        assertThat(inventoryService.cellBags(BT, "PLASMA"))
                .extracting(BagVO::bagNo).containsExactly("ONLY-PLASMA");
    }

    @Test
    void expiredCryoScrap_syncsMergedPlasmaCell() {
        seedInterleavedBags();
        // 把最近效期那袋冷沉淀改成昨天过期
        BloodBag near = bagRepository.findByBagNo("C-NEAR").orElseThrow();
        near.setExpiryDate(LocalDate.now().minusDays(1));
        bagRepository.save(near);

        int scrapped = inventoryService.scrapExpired();
        assertThat(scrapped).isEqualTo(1);
        // AFTER_COMMIT 监听器已同步血浆格：3 袋、最近效期变为血浆 200 天那袋
        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(inv.getBagCount()).isEqualTo(3);
        assertThat(inv.getNearestExpiry()).isEqualTo(LocalDate.now().plusDays(200));
        assertThat(scrapRepository.findAll())
                .anyMatch(s -> "C-NEAR".equals(s.getBagNo()) && "EXPIRED".equals(s.getReason()));
    }

    @Test
    void restartReconcile_repairsDriftAndMatchesDetails() {
        seedInterleavedBags();
        // 模拟重启前汇总行漂移：袋数 / U 数 / 最近效期全部写错
        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        inv.setBagCount(99);
        inv.setUnitCount(99);
        inv.setNearestExpiry(LocalDate.now().plusDays(999));
        inventoryRepository.saveAndFlush(inv);

        int cells = txTemplate.execute(s -> inventoryService.reconcileAll());
        assertThat(cells).isEqualTo(BloodDict.BLOOD_TYPES.size() * BloodDict.COMPONENTS.size());

        BloodInventory repaired = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(repaired.getBagCount()).isEqualTo(4);
        assertThat(repaired.getUnitCount()).isEqualTo(4);
        assertThat(repaired.getNearestExpiry()).isEqualTo(LocalDate.now().plusDays(3));
        // 空格不留行：本次没有全血 / 红细胞 / 血小板血袋
        assertThat(inventoryRepository.findByBloodTypeAndComponent(BT, "RBC")).isEmpty();
    }

    @Test
    void matrixCellLevel_reflectsNearExpiryFromCryo() {
        // 血浆 1 袋远期 + 冷沉淀 1 袋 3 天到期：血浆格应染「近效期」色（预警线 2 袋，2 袋不缺）
        saveBag("P1", "PLASMA", 220, LocalDate.now().plusDays(300));
        saveBag("C1", "CRYO", 30, LocalDate.now().plusDays(3));
        inventoryService.syncInventory(BT, "PLASMA");

        var matrix = inventoryService.matrix(BT);
        var plasmaCell = matrix.cells().stream()
                .filter(c -> "PLASMA".equals(c.component())).findFirst().orElseThrow();
        assertThat(plasmaCell.bagCount()).isEqualTo(2);
        assertThat(plasmaCell.level()).isEqualTo("NEAR");
        assertThat(plasmaCell.daysToExpiry()).isEqualTo(3);
    }

    @Test
    void sampleReleaseProducesCryo_intoPlasmaCell_viaDomainEvent() {
        // 400ml 全血放行 → AFTER_COMMIT 监听器自动制备：RBC + PLASMA + PLATELET + CRYO 各 1 袋
        LocalDate today = LocalDate.now();
        txTemplate.executeWithoutResult(s -> {
            CollectionRecord collection = new CollectionRecord();
            collection.setCollectNo("C-E2E-1");
            collection.setStationCode("P02");
            collection.setVolumeMl(400);
            collection.setCollectDate(today.minusDays(1));
            collection.setNurse("测试护士");
            collection.setStatus("DONE");
            collection.setCreatedAt(java.time.LocalDateTime.now());
            collectionRepository.save(collection);

            TestSample sample = new TestSample();
            sample.setBarcode("S-E2E-1");
            sample.setCollection(collection);
            sample.setDonorName("全流程献血者");
            sample.setBloodType(BT);
            sample.setStatus("TESTING");
            sample.setCreatedAt(java.time.LocalDateTime.now());
            sampleRepository.saveAndFlush(sample);       // 先落库拿到 id，模拟生产中「检测中→放行」的已托管标本
            sample.release("放行复核人");                 // 事件携带真实 sampleId，AFTER_COMMIT 触发自动制备
            sampleRepository.save(sample);
        });

        // 事件已在独立事务里完成制备 + 库存同步
        long cryoBags = bagRepository.findAll().stream()
                .filter(b -> "CRYO".equals(b.getComponent()) && "AVAILABLE".equals(b.getStatus())).count();
        assertThat(cryoBags).isEqualTo(1);

        BloodInventory plasma = inventoryRepository.findByBloodTypeAndComponent(BT, "PLASMA").orElseThrow();
        assertThat(plasma.getBagCount()).isEqualTo(2);   // 血浆 1 + 冷沉淀 1
        List<BagVO> drawer = inventoryService.cellBags(BT, "PLASMA");
        assertThat(drawer).extracting(BagVO::component)
                .containsExactlyInAnyOrder("PLASMA", "CRYO");

        BloodInventory rbc = inventoryRepository.findByBloodTypeAndComponent(BT, "RBC").orElseThrow();
        assertThat(rbc.getBagCount()).isEqualTo(1);
        BloodInventory platelet = inventoryRepository.findByBloodTypeAndComponent(BT, "PLATELET").orElseThrow();
        assertThat(platelet.getBagCount()).isEqualTo(1);
    }

    // ---------------- 辅助构造 ----------------

    private HospitalRequest newRequest(String component, int unitsU) {
        HospitalRequest request = new HospitalRequest();
        request.setRequestNo("RQ-T-" + System.nanoTime());
        request.setHospital("测试医院");
        request.setPatientName("测试患者");
        request.setPatientBloodType(BT);
        request.setComponent(component);
        request.setUnitsU(unitsU);
        request.setUrgency("URGENT");
        request.setStatus("PENDING");
        request.setIssuedU(0);
        return requestRepository.save(request);
    }

    private CrossMatch newMatch(HospitalRequest request, BloodBag bag, int unitsU) {
        CrossMatch match = new CrossMatch();
        match.setMatchNo("XM-T-" + System.nanoTime());
        match.setRequest(request);
        match.setBag(bag);
        match.setBagNo(bag.getBagNo());
        match.setDonorName(bag.getDonorName());
        match.setBloodType(bag.getBloodType());
        match.setComponent(bag.getComponent());
        match.setExpiryDate(bag.getExpiryDate());
        match.setRecipient(request.getPatientName());
        match.setHospital(request.getHospital());
        match.setUnitsU(unitsU);
        match.setUrgency(request.getUrgency());
        match.setResult("COMPATIBLE");
        match.setOperator("姜妍");
        match.setMatchTime(java.time.LocalDateTime.now());
        match.setIssued(Boolean.FALSE);
        match.setRecoveryChecked(Boolean.FALSE);
        return matchRepository.save(match);
    }

    private void releaseSampleForBag(BloodBag bag) {
        CollectionRecord collection = new CollectionRecord();
        collection.setCollectNo("C-T-" + System.nanoTime());
        collection.setStationCode("P01");
        collection.setVolumeMl(400);
        collection.setCollectDate(LocalDate.now().minusDays(2));
        collection.setStatus("DONE");
        collectionRepository.save(collection);

        TestSample sample = new TestSample();
        sample.setBarcode("S-T-" + System.nanoTime());
        sample.setCollection(collection);
        sample.setDonorName("测试献血者");
        sample.setBloodType(BT);
        sample.setStatus("RELEASED");
        sample.setReviewer("复核人");
        sampleRepository.save(sample);

        bag.setCollection(collection);
        bagRepository.save(bag);
    }
}
