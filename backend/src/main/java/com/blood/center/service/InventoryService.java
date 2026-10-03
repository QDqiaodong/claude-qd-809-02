package com.blood.center.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.BloodBag;
import com.blood.center.domain.BloodInventory;
import com.blood.center.domain.ScrapRecord;
import com.blood.center.dto.BagVO;
import com.blood.center.dto.ComponentSummaryVO;
import com.blood.center.dto.InventoryAdjustRequest;
import com.blood.center.dto.InventoryCellVO;
import com.blood.center.dto.InventoryMatrixVO;
import com.blood.center.dto.ScrapVO;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.BloodInventoryRepository;
import com.blood.center.repository.ScrapRecordRepository;

/**
 * 血液库存：血型 × 成分 二维矩阵、格子明细、出入库与报废。
 */
@Service
public class InventoryService {

    /** Redis：累计入库袋数 */
    public static final String INBOUND_COUNTER = "blood:center:inbound:count";

    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BloodInventoryRepository inventoryRepository;
    private final BloodBagRepository bagRepository;
    private final ScrapRecordRepository scrapRepository;
    private final StringRedisTemplate redis;

    public InventoryService(BloodInventoryRepository inventoryRepository, BloodBagRepository bagRepository,
                            ScrapRecordRepository scrapRepository, StringRedisTemplate redis) {
        this.inventoryRepository = inventoryRepository;
        this.bagRepository = bagRepository;
        this.scrapRepository = scrapRepository;
        this.redis = redis;
    }

    /**
     * 二维库存矩阵：8 种血型 × 4 种成分，低于预警线 / 近效期按深浅染色。
     */
    @Transactional(readOnly = true)
    public InventoryMatrixVO matrix(String bloodType) {
        List<String> bloodTypes = bloodType == null || bloodType.isBlank()
                ? BloodDict.BLOOD_TYPES
                : List.of(bloodType);
        List<InventoryMatrixVO.ColumnVO> columns = new ArrayList<>();
        for (String component : BloodDict.COMPONENTS) {
            columns.add(new InventoryMatrixVO.ColumnVO(component, BloodDict.componentName(component)));
        }
        List<InventoryCellVO> cells = new ArrayList<>();
        for (String bt : bloodTypes) {
            for (String component : BloodDict.COMPONENTS) {
                BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(bt, component).orElse(null);
                cells.add(toCell(inv, bt, component));
            }
        }
        return new InventoryMatrixVO(bloodTypes, columns, cells, legend());
    }

    /**
     * 矩阵某一格的血袋明细：与格子统计同一口径——
     * 血浆格返回 PLASMA + CRYO 的全部可用袋，按效期升序（与出库扣减顺序一致）。
     */
    @Transactional(readOnly = true)
    public List<BagVO> cellBags(String bloodType, String component) {
        if (!BloodDict.isValidBloodType(bloodType)) {
            throw new BizException("未知的血型：" + bloodType);
        }
        if (!BloodDict.COMPONENTS.contains(component)) {
            throw new BizException("未知的库存成分格：" + component);
        }
        List<BagVO> vos = new ArrayList<>();
        for (BloodBag bag : availableBagsOfCell(bloodType, component)) {
            vos.add(toVO(bag));
        }
        return vos;
    }

    /**
     * 格子出入库：IN 入库（生成血袋并计入库存），OUT 出库（按格子内最早效期扣减并写报废记录）。
     * 血浆格入库默认生成 PLASMA 袋；制备产出的冷沉淀（CRYO）同样落入本格。
     * 血浆格出库在「血浆 + 冷沉淀」全部可用袋中按最早效期扣减，与页面所示最近效期、可选范围一致。
     */
    @Transactional
    public InventoryCellVO adjust(InventoryAdjustRequest request) {
        if (!BloodDict.isValidBloodType(request.bloodType())) {
            throw new BizException("未知的血型：" + request.bloodType());
        }
        if (!BloodDict.isValidComponent(request.component())) {
            throw new BizException("未知的血液成分：" + request.component());
        }
        String cell = BloodDict.inventoryComponent(request.component());
        int bags = request.bags() == null ? 1 : request.bags();
        if (bags <= 0) {
            throw new BizException("出入库袋数必须大于 0");
        }
        int volume = request.volumeMl() == null
                ? defaultVolume(request.component()) : request.volumeMl();
        String mode = request.mode() == null || request.mode().isBlank() ? "IN" : request.mode().toUpperCase();
        LocalDate today = LocalDate.now();

        if ("IN".equals(mode)) {
            for (int i = 0; i < bags; i++) {
                BloodBag bag = new BloodBag();
                bag.setBagNo("B" + today.format(SEQ_DAY) + "-"
                        + String.format("%03d", bagRepository.count() + 1));
                bag.setBloodType(request.bloodType());
                bag.setComponent(request.component());
                bag.setVolumeMl(volume);
                bag.setCollectDate(today.minusDays(1));
                bag.setExpiryDate(request.expiryDate() == null
                        ? today.plusDays(BloodDict.shelfDays(request.component()))
                        : request.expiryDate());
                bag.setStatus("AVAILABLE");
                bag.setDonorName("手工入库");
                bag.setCreatedAt(LocalDateTime.now());
                bagRepository.save(bag);
            }
            redis.opsForValue().increment(INBOUND_COUNTER, bags);
        } else if ("OUT".equals(mode)) {
            // 出库只认库存格：不论请求里传 PLASMA 还是 CRYO，都在合并后的同一份可用袋中按最早效期扣减
            List<BloodBag> available = availableBagsOfCell(request.bloodType(), cell);
            if (available.size() < bags) {
                throw new BizException(request.bloodType() + " " + BloodDict.componentName(cell)
                        + (BloodDict.isMergedCell(cell) ? "（含冷沉淀）" : "")
                        + " 可用血袋仅 " + available.size() + " 袋，不足出库 " + bags + " 袋");
            }
            for (int i = 0; i < bags; i++) {
                BloodBag bag = available.get(i);
                bag.setStatus("SCRAPPED");
                bagRepository.save(bag);
                scrapRepository.save(buildScrap(bag.getBagNo(), bag.getBloodType(), bag.getComponent(),
                        1, bag.getVolumeMl(), "DAMAGED",
                        request.operator() == null ? "库存出库" : request.operator(),
                        request.remark() == null ? "手工出库" : request.remark()));
            }
        } else {
            throw new BizException("未知的出入库方向：" + request.mode());
        }
        syncInventory(request.bloodType(), cell);
        return toCell(inventoryRepository
                .findByBloodTypeAndComponent(request.bloodType(), cell).orElse(null),
                request.bloodType(), cell);
    }

    /**
     * 过期扫描：把已过期的可用血袋判废，领域事件会生成报废记录并扣减库存。
     */
    @Transactional
    public int scrapExpired() {
        List<BloodBag> expired = bagRepository.findExpiredAvailable(LocalDate.now());
        for (BloodBag bag : expired) {
            bag.markExpired("EXPIRED");
            bagRepository.save(bag);
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public List<ScrapVO> listScraps(String bloodType) {
        List<ScrapRecord> records = bloodType == null || bloodType.isBlank()
                ? scrapRepository.findTop50ByOrderByIdDesc()
                : scrapRepository.findByBloodTypeOrderByIdDesc(bloodType);
        List<ScrapVO> vos = new ArrayList<>();
        for (ScrapRecord record : records) {
            vos.add(toVO(record));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public List<ComponentSummaryVO> summary() {
        List<ComponentSummaryVO> list = new ArrayList<>();
        for (ComponentSummaryVO raw : inventoryRepository.summaryByBloodTypeAndComponent()) {
            list.add(new ComponentSummaryVO(raw.bloodType(), raw.component(),
                    BloodDict.componentName(raw.component()), raw.bagCount(), raw.unitCount(), raw.nearestExpiry()));
        }
        return list;
    }

    // ---------------- 内部方法 ----------------

    /**
     * 依据可用血袋重算某一格的袋数 / U 数 / 最近效期。
     * 冷沉淀（CRYO）并入血浆格：血浆格的可用袋 = PLASMA + CRYO，最近效期取两者合并后最早的一袋。
     */
    @Transactional
    public void syncInventory(String bloodType, String component) {
        reconcileCell(bloodType, BloodDict.inventoryComponent(component));
    }

    /**
     * 全量重算全部「血型 × 成分」库存格。
     * 服务启动时执行一次：任何历史漂移（种子数据未计冷沉淀、手工改库、漏同步）都会被拉回到
     * 与血袋明细一致的状态，保证刷新 / 重启后矩阵、明细、记录口径相同。
     */
    @Transactional
    public int reconcileAll() {
        int cells = 0;
        for (String bloodType : BloodDict.BLOOD_TYPES) {
            for (String component : BloodDict.COMPONENTS) {
                reconcileCell(bloodType, component);
                cells++;
            }
        }
        return cells;
    }

    /**
     * 库存格背后真正可用的血袋（血浆格 = 血浆 + 冷沉淀），按效期升序、同效期按袋号稳定排序。
     * 矩阵统计、格子明细、FIFO 出库共用此方法，从根上保证三处口径一致。
     */
    private List<BloodBag> availableBagsOfCell(String bloodType, String cellComponent) {
        return bagRepository.findByBloodTypeAndComponentInAndStatusOrderByExpiryDateAscIdAsc(
                bloodType, BloodDict.cellComponents(cellComponent), "AVAILABLE");
    }

    /** 重算单个库存格（入参必须是矩阵格成分：WHOLE/RBC/PLASMA/PLATELET） */
    private void reconcileCell(String bloodType, String cell) {
        List<BloodBag> available = availableBagsOfCell(bloodType, cell);
        BloodInventory inv = inventoryRepository.findByBloodTypeAndComponent(bloodType, cell).orElse(null);
        // 空格不建行：全新库没有任何血袋时不制造 32 行 0 库存（否则顶部「低于预警线」KPI 恒为 32）；
        // 已有行则照常归零修正，保证报废 / 出库清空后矩阵仍与明细一致。
        if (inv == null && available.isEmpty()) {
            return;
        }
        if (inv == null) {
            inv = new BloodInventory();
            inv.setBloodType(bloodType);
            inv.setComponent(cell);
            inv.setWarningLine(defaultWarningLine(cell));
        }
        inv.setBagCount(available.size());
        inv.setUnitCount(available.stream()
                .mapToInt(b -> BloodDict.unitsOf(b.getVolumeMl() == null ? 0 : b.getVolumeMl())).sum());
        inv.setNearestExpiry(available.isEmpty() ? null : available.get(0).getExpiryDate());
        inv.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inv);
    }

    private int defaultWarningLine(String component) {
        return switch (component) {
            case "WHOLE" -> 2;
            case "RBC" -> 2;
            case "PLASMA" -> 2;
            default -> 2;
        };
    }

    private int defaultVolume(String component) {
        return switch (component) {
            case "WHOLE" -> 400;
            case "PLASMA" -> 220;
            case "PLATELET" -> 250;
            default -> 200;
        };
    }

    private ScrapRecord buildScrap(String bagNo, String bloodType, String component, int bags, int volume,
                                   String reason, String operator, String remark) {
        ScrapRecord record = new ScrapRecord();
        record.setScrapNo("SC" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", scrapRepository.count() + 1));
        record.setBagNo(bagNo);
        record.setBloodType(bloodType);
        record.setComponent(component);
        record.setBags(bags);
        record.setVolumeMl(volume);
        record.setReason(reason);
        record.setOperator(operator);
        record.setScrapTime(LocalDateTime.now());
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private InventoryCellVO toCell(BloodInventory inv, String bloodType, String component) {
        int bagCount = inv == null || inv.getBagCount() == null ? 0 : inv.getBagCount();
        int unitCount = inv == null || inv.getUnitCount() == null ? 0 : inv.getUnitCount();
        int warningLine = inv == null || inv.getWarningLine() == null ? defaultWarningLine(component)
                : inv.getWarningLine();
        LocalDate expiry = inv == null ? null : inv.getNearestExpiry();
        Integer days = expiry == null ? null : (int) ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        String level = "OK";
        if (days != null && days <= 0) {
            level = "EXPIRED";              // 最近效期已过，禁止出库
        } else if (bagCount <= 0) {
            level = "LOW";                  // 断供
        } else if (bagCount < warningLine) {
            level = "WARN";                 // 低于预警线
        } else if (days != null && days <= BloodDict.NEAR_EXPIRY_DAYS) {
            level = "NEAR";                 // 近效期
        }
        return new InventoryCellVO(bloodType, component, BloodDict.componentName(component), bagCount,
                unitCount, expiry, warningLine, days, level);
    }

    private List<InventoryMatrixVO.LegendVO> legend() {
        return List.of(
                new InventoryMatrixVO.LegendVO("OK", "充足", "#880e4f", "袋数高于预警线，效期充裕"),
                new InventoryMatrixVO.LegendVO("NEAR", "近效期", "#ad1457", "最近效期 7 天内"),
                new InventoryMatrixVO.LegendVO("WARN", "低于预警线", "#c2185b", "袋数低于预警线"),
                new InventoryMatrixVO.LegendVO("LOW", "紧缺", "#e91e63", "袋数不足预警线一半"),
                new InventoryMatrixVO.LegendVO("EXPIRED", "已过期", "#4e342e", "最近效期已过，禁止出库"));
    }

    private BagVO toVO(BloodBag bag) {
        Integer days = bag.getExpiryDate() == null ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), bag.getExpiryDate());
        return new BagVO(bag.getId(), bag.getBagNo(), bag.getBloodType(), bag.getComponent(),
                BloodDict.componentName(bag.getComponent()), bag.getVolumeMl(),
                BloodDict.unitsOf(bag.getVolumeMl() == null ? 0 : bag.getVolumeMl()),
                bag.getCollectDate(), bag.getExpiryDate(), days, bag.getStatus(),
                BloodDict.bagStatusName(bag.getStatus()), bag.getDonorName(),
                bag.getBatch() == null ? null : bag.getBatch().getBatchNo());
    }

    private ScrapVO toVO(ScrapRecord record) {
        return new ScrapVO(record.getId(), record.getScrapNo(), record.getBagNo(), record.getBloodType(),
                record.getComponent(), BloodDict.componentName(record.getComponent()), record.getBags(),
                record.getVolumeMl(), record.getReason(), BloodDict.scrapReasonName(record.getReason()),
                record.getOperator(), record.getScrapTime(), record.getRemark());
    }
}
