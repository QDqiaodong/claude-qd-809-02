package com.blood.center.listener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blood.center.common.BloodDict;
import com.blood.center.domain.BloodBag;
import com.blood.center.domain.CollectionRecord;
import com.blood.center.domain.CrossMatch;
import com.blood.center.domain.HospitalRequest;
import com.blood.center.domain.PreparationBatch;
import com.blood.center.domain.ScrapRecord;
import com.blood.center.domain.TestSample;
import com.blood.center.event.BloodExpiredEvent;
import com.blood.center.event.BloodIssuedEvent;
import com.blood.center.event.CollectionCompletedEvent;
import com.blood.center.event.SampleReleasedEvent;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.CollectionRecordRepository;
import com.blood.center.repository.CrossMatchRepository;
import com.blood.center.repository.HospitalRequestRepository;
import com.blood.center.repository.PreparationBatchRepository;
import com.blood.center.repository.ScrapRecordRepository;
import com.blood.center.repository.TestSampleRepository;
import com.blood.center.service.InventoryService;
import com.blood.center.service.LabService;

/**
 * 领域事件监听器：采血 → 检验 → 制备 → 库存 → 发血 的跨聚合联动。
 * 全部使用 AFTER_COMMIT，提交成功后才产生副作用，失败回滚不留脏数据。
 */
@Component
public class BloodEventListener {

    private static final Logger log = LoggerFactory.getLogger(BloodEventListener.class);
    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Redis：发血流水计数与最近流水 */
    public static final String ISSUE_COUNTER = "blood:center:issue:count";
    public static final String ISSUE_FLOW = "blood:center:issue:flow";

    private final CollectionRecordRepository collectionRepository;
    private final TestSampleRepository sampleRepository;
    private final PreparationBatchRepository preparationRepository;
    private final BloodBagRepository bagRepository;
    private final ScrapRecordRepository scrapRepository;
    private final CrossMatchRepository matchRepository;
    private final HospitalRequestRepository requestRepository;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redis;

    public BloodEventListener(CollectionRecordRepository collectionRepository,
                              TestSampleRepository sampleRepository,
                              PreparationBatchRepository preparationRepository,
                              BloodBagRepository bagRepository,
                              ScrapRecordRepository scrapRepository,
                              CrossMatchRepository matchRepository,
                              HospitalRequestRepository requestRepository,
                              InventoryService inventoryService,
                              StringRedisTemplate redis) {
        this.collectionRepository = collectionRepository;
        this.sampleRepository = sampleRepository;
        this.preparationRepository = preparationRepository;
        this.bagRepository = bagRepository;
        this.scrapRepository = scrapRepository;
        this.matchRepository = matchRepository;
        this.requestRepository = requestRepository;
        this.inventoryService = inventoryService;
        this.redis = redis;
    }

    /**
     * ① 采血完成 → 自动生成检验标本（进入「待检」轨道）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCollectionCompleted(CollectionCompletedEvent event) {
        if (event.collectionId() == null) {
            return;
        }
        CollectionRecord record = collectionRepository.findById(event.collectionId()).orElse(null);
        if (record == null) {
            return;
        }
        if (sampleRepository.findByCollectionId(record.getId()).isPresent()) {
            return;
        }
        TestSample sample = new TestSample();
        sample.setBarcode("S" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", sampleRepository.count() + 1));
        sample.setCollection(record);
        sample.setDonorName(record.getDonor() == null ? null : record.getDonor().getName());
        sample.setBloodType(record.getDonor() == null ? "O+" : record.getDonor().getBloodType());
        sample.setStatus("PENDING");
        sample.setCreatedAt(LocalDateTime.now());
        sampleRepository.save(sample);
        log.info("[领域事件] 采血 {} 完成，自动生成标本 {}", record.getCollectNo(), sample.getBarcode());
    }

    /**
     * ② 标本检验放行 → 自动生成成分制备单，并把分离出的血袋计入库存
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSampleReleased(SampleReleasedEvent event) {
        TestSample sample = event.sampleId() == null ? null : sampleRepository.findById(event.sampleId()).orElse(null);
        if (sample == null) {
            return;
        }
        CollectionRecord collection = sample.getCollection();
        if (collection == null) {
            return;
        }
        if (!preparationRepository.findByCollectionId(collection.getId()).isEmpty()) {
            return;                                   // 同一袋全血只制备一次
        }
        int volume = collection.getVolumeMl() == null ? 0 : collection.getVolumeMl();
        int[] split = LabService.splitComponents(volume);

        PreparationBatch batch = new PreparationBatch();
        batch.setBatchNo("PB" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", preparationRepository.count() + 1));
        batch.setCollection(collection);
        batch.setCollectNo(collection.getCollectNo());
        batch.setBloodType(sample.getBloodType());
        batch.setRbcBags(1);
        batch.setRbcVolumeMl(split[0]);
        batch.setPlasmaBags(1);
        batch.setPlasmaVolumeMl(split[1]);
        batch.setPlateletBags(split[2] > 0 ? 1 : 0);
        batch.setPlateletVolumeMl(split[2]);
        batch.setCryoBags(split[3] > 0 ? 1 : 0);
        batch.setCryoVolumeMl(split[3]);
        batch.setOperator("制备 " + (sample.getReviewer() == null ? "自动线" : sample.getReviewer()));
        batch.setPreparedTime(LocalDateTime.now());
        batch.setStatus("DONE");
        batch.setCreatedAt(LocalDateTime.now());
        PreparationBatch saved = preparationRepository.save(batch);

        List<BloodBag> produced = new ArrayList<>();
        produced.add(newBag(sample.getBloodType(), "RBC", split[0], collection, saved));
        produced.add(newBag(sample.getBloodType(), "PLASMA", split[1], collection, saved));
        if (split[2] > 0) {
            produced.add(newBag(sample.getBloodType(), "PLATELET", split[2], collection, saved));
        }
        if (split[3] > 0) {
            produced.add(newBag(sample.getBloodType(), "CRYO", split[3], collection, saved));
        }
        for (BloodBag bag : produced) {
            bag.setBagNo(nextBagNo());
            bagRepository.save(bag);
        }
        inventoryService.syncInventory(sample.getBloodType(), "RBC");
        inventoryService.syncInventory(sample.getBloodType(), "PLASMA");
        inventoryService.syncInventory(sample.getBloodType(), "PLATELET");
        log.info("[领域事件] 标本 {} 放行，自动生成制备批次 {}，产出 {} 袋",
                sample.getBarcode(), saved.getBatchNo(), produced.size());
    }

    /**
     * ③ 血液过期 / 临近效期 → 自动生成报废记录并从可用库存扣减
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBloodExpired(BloodExpiredEvent event) {
        ScrapRecord record = new ScrapRecord();
        record.setScrapNo("SC" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", scrapRepository.count() + 1));
        record.setBagNo(event.bagNo());
        record.setBloodType(event.bloodType());
        record.setComponent(event.component());
        record.setBags(1);
        record.setVolumeMl(event.volumeMl());
        record.setReason(event.reason() == null ? "EXPIRED" : event.reason());
        record.setOperator("系统自动报废");
        record.setScrapTime(LocalDateTime.now());
        record.setRemark("血液超过失效日期，由过期扫描自动生成报废记录");
        record.setCreatedAt(LocalDateTime.now());
        scrapRepository.save(record);

        inventoryService.syncInventory(event.bloodType(), event.component());
        log.info("[领域事件] 血袋 {} 过期，自动生成报废单 {}", event.bagNo(), record.getScrapNo());
    }

    /**
     * ④ 发血出库 → 扣减「血型 × 成分」库存、记录发血流水、回写用血申请
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBloodIssued(BloodIssuedEvent event) {
        CrossMatch match = matchRepository.findById(event.matchId()).orElse(null);
        if (match != null && match.getBag() != null) {
            BloodBag bag = match.getBag();
            // 发血事务已把血袋条件式置为 ISSUED；此处只作兜底，绝不覆盖同源隔离 / 已报废状态
            if ("AVAILABLE".equals(bag.getStatus())) {
                bag.markIssued();
                bagRepository.save(bag);
            }
        }
        inventoryService.syncInventory(event.bloodType(), event.component());

        if (event.requestId() != null) {
            HospitalRequest request = requestRepository.findById(event.requestId()).orElse(null);
            if (request != null) {
                int issued = (request.getIssuedU() == null ? 0 : request.getIssuedU())
                        + (event.unitsU() == null ? 0 : event.unitsU());
                request.setIssuedU(Math.min(issued, request.getUnitsU() == null ? issued : request.getUnitsU()));
                request.setStatus(request.getIssuedU() >= (request.getUnitsU() == null ? 0 : request.getUnitsU())
                        ? "DONE" : "PARTIAL");
                requestRepository.save(request);
            }
        }
        redis.opsForValue().increment(ISSUE_COUNTER);
        redis.opsForList().leftPush(ISSUE_FLOW, LocalDateTime.now()
                + " " + event.matchNo() + " " + event.bloodType() + BloodDict.componentName(event.component())
                + " " + event.unitsU() + "U");
        redis.opsForList().trim(ISSUE_FLOW, 0, 49);
        log.info("[领域事件] 配血单 {} 发血出库，扣减 {} {} 库存 {} U",
                event.matchNo(), event.bloodType(), event.component(), event.unitsU());
    }

    /** 袋号必须在每次落库前重新取一遍序号，否则同一批次内会撞号 */
    private String nextBagNo() {
        return "B" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", bagRepository.count() + 1);
    }

    private BloodBag newBag(String bloodType, String component, int volume, CollectionRecord collection,
                            PreparationBatch batch) {
        BloodBag bag = new BloodBag();
        bag.setBloodType(bloodType);
        bag.setComponent(component);
        bag.setVolumeMl(volume);
        bag.setCollectDate(collection.getCollectDate() == null ? LocalDate.now() : collection.getCollectDate());
        bag.setExpiryDate(LocalDate.now().plusDays(BloodDict.shelfDays(component)));
        bag.setStatus("AVAILABLE");
        bag.setDonorName(collection.getDonor() == null ? null : collection.getDonor().getName());
        bag.setCollection(collection);
        bag.setBatch(batch);
        bag.setCreatedAt(LocalDateTime.now());
        return bag;
    }
}
