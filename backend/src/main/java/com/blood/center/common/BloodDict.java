package com.blood.center.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 血站业务词典与规则参数：血型、成分、采血位、检验项目、业务红线。
 * 规则集中在此，便于被要求调整或扩展。
 */
public final class BloodDict {

    private BloodDict() {
    }

    /** 8 种血型（顶部导航维度） */
    public static final List<String> BLOOD_TYPES = List.of("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-");

    /** 库存二维矩阵的 4 种成分 */
    public static final List<String> COMPONENTS = List.of("WHOLE", "RBC", "PLASMA", "PLATELET");

    /** 制备可分离出的成分（含冷沉淀） */
    public static final List<String> PREP_COMPONENTS = List.of("RBC", "PLASMA", "PLATELET", "CRYO");

    private static final Map<String, String> COMPONENT_NAME = Map.of(
            "WHOLE", "全血",
            "RBC", "红细胞悬液",
            "PLASMA", "血浆",
            "PLATELET", "血小板",
            "CRYO", "冷沉淀");

    /** 冷沉淀属血浆制品，库存并入血浆格统计 */
    private static final Map<String, String> COMPONENT_TO_INVENTORY = Map.of("CRYO", "PLASMA");

    /** 各成分保存天数（用于算失效日期） */
    private static final Map<String, Integer> SHELF_DAYS = Map.of(
            "WHOLE", 35,
            "RBC", 42,
            "PLASMA", 365,
            "PLATELET", 5,
            "CRYO", 365);

    /** 采血位（状态盘上圆环排布的 12 个位） */
    public static final List<String> STATIONS = List.of(
            "P01", "P02", "P03", "P04", "P05", "P06",
            "P07", "P08", "P09", "P10", "P11", "P12");

    // ---------------- 业务红线 ----------------
    /** 两次全血献血最小间隔（月） */
    public static final int MIN_INTERVAL_MONTHS = 6;

    /** 单次采血绝对上限 ml */
    public static final int MAX_SINGLE_VOLUME_ML = 400;

    /** 单次采血下限 ml */
    public static final int MIN_SINGLE_VOLUME_ML = 200;

    /** 采血量不超过体重的 8 ml/kg，并按 50 ml 取整 */
    public static final BigDecimal VOLUME_PER_KG = BigDecimal.valueOf(8);

    /** ALT 上限 U/L */
    public static final BigDecimal ALT_LIMIT = BigDecimal.valueOf(40);

    /** 血红蛋白下限 g/L（男） */
    public static final BigDecimal HB_LIMIT_MALE = BigDecimal.valueOf(120);

    /** 血红蛋白下限 g/L（女） */
    public static final BigDecimal HB_LIMIT_FEMALE = BigDecimal.valueOf(115);

    /** 近效期预警天数 */
    public static final int NEAR_EXPIRY_DAYS = 7;

    /** 1 U 折算容量 ml */
    public static final int ML_PER_UNIT = 200;

    // ---------------- 状态字典 ----------------
    private static final Map<String, String> COLLECTION_STATUS = Map.of(
            "COLLECTING", "采血中",
            "DONE", "已完成",
            "ABNORMAL", "异常");

    private static final Map<String, String> STATION_STATUS = Map.of(
            "IDLE", "空闲",
            "OCCUPIED", "占用",
            "CLEANING", "消毒中",
            "OTHER", "其它血型");

    private static final Map<String, String> SAMPLE_STATUS = Map.of(
            "PENDING", "待检",
            "TESTING", "检测中",
            "RELEASED", "已放行",
            "SCRAPPED", "报废");

    private static final Map<String, String> BAG_STATUS = Map.of(
            "AVAILABLE", "可用",
            "QUARANTINED", "同源隔离",
            "ISSUED", "已发出",
            "SCRAPPED", "已报废");

    private static final Map<String, String> SCRAP_REASON = Map.of(
            "EXPIRED", "过期",
            "DAMAGED", "破损",
            "FAILED", "检验不合格",
            "REACTION", "输血反应同源");

    private static final Map<String, String> REACTION_STATUS = Map.of(
            "OPEN", "在办",
            "EXCLUDED", "排除关联",
            "CONFIRMED", "确认风险");

    private static final Map<String, String> REACTION_REVIEW = Map.of(
            "EXCLUDED", "排除关联",
            "CONFIRMED", "确认风险");

    private static final Map<String, String> REACTION_LINK_TYPE = Map.of(
            "ISOLATED", "同源隔离袋",
            "TRACE", "仅追溯");

    private static final Map<String, String> REACTION_LINK_STATUS = Map.of(
            "QUARANTINED", "隔离中",
            "RELEASED", "已释放",
            "SCRAPPED", "已报废",
            "TRACED", "仅追溯",
            "KEPT", "保持隔离");

    private static final Map<String, String> REACTION_LEDGER_ACTION = Map.of(
            "REPORT", "上报",
            "ISOLATE", "隔离",
            "RELEASE", "释放",
            "SCRAP", "报废",
            "REVIEW", "复核",
            "KEEP", "保持隔离");

    private static final Map<String, String> URGENCY = Map.of(
            "ROUTINE", "常规",
            "URGENT", "紧急",
            "CRITICAL", "特急");

    private static final Map<String, String> REQUEST_STATUS = Map.of(
            "PENDING", "待发血",
            "PARTIAL", "部分发放",
            "DONE", "已发放",
            "CANCELED", "已取消");

    private static final Map<String, String> MATCH_RESULT = Map.of(
            "PENDING", "未判定",
            "COMPATIBLE", "相合",
            "SUSPECT", "可疑",
            "INCOMPATIBLE", "不相合");

    private static final Map<String, String> TEST_RESULT = Map.of(
            "NEGATIVE", "阴性",
            "POSITIVE", "阳性");

    /** 六个检验项目编码（复测一轮只针对其中一个项目） */
    public static final List<String> TEST_ITEMS = List.of("ALT", "HB", "HBV", "HCV", "HIV", "SYPHILIS");

    private static final Map<String, String> TEST_ITEM_NAME = Map.of(
            "ALT", "ALT",
            "HB", "血红蛋白",
            "HBV", "HBV",
            "HCV", "HCV",
            "HIV", "HIV",
            "SYPHILIS", "梅毒");

    /** 数值型项目（取 newValue）；其余为阴阳性项目（取 newResult） */
    private static final List<String> NUMERIC_TEST_ITEMS = List.of("ALT", "HB");

    // ---------------- 取值方法 ----------------

    public static boolean isValidBloodType(String bloodType) {
        return bloodType != null && BLOOD_TYPES.contains(bloodType);
    }

    public static String componentName(String component) {
        return component == null ? "" : COMPONENT_NAME.getOrDefault(component, component);
    }

    /** 库存归属成分：冷沉淀并入血浆 */
    public static String inventoryComponent(String component) {
        return COMPONENT_TO_INVENTORY.getOrDefault(component, component);
    }

    public static int shelfDays(String component) {
        return SHELF_DAYS.getOrDefault(component, 35);
    }

    /**
     * 按体重计算单次允许采血上限：体重(kg) × 8 ml，向下取整到 50 ml，且不超过 400 ml。
     */
    public static int maxVolumeByWeight(BigDecimal weightKg) {
        BigDecimal weight = weightKg == null ? BigDecimal.ZERO : weightKg;
        int raw = weight.multiply(VOLUME_PER_KG).intValue();
        int stepped = (raw / 50) * 50;
        if (stepped > MAX_SINGLE_VOLUME_ML) {
            return MAX_SINGLE_VOLUME_ML;
        }
        if (stepped < MIN_SINGLE_VOLUME_ML) {
            return MIN_SINGLE_VOLUME_ML;
        }
        return stepped;
    }

    /** 容量折算 U 数 */
    public static int unitsOf(int volumeMl) {
        int units = volumeMl / ML_PER_UNIT;
        return units < 1 ? 1 : units;
    }

    public static String collectionStatusName(String status) {
        return status == null ? "" : COLLECTION_STATUS.getOrDefault(status, status);
    }

    public static String stationStatusName(String status) {
        return status == null ? "" : STATION_STATUS.getOrDefault(status, status);
    }

    public static String sampleStatusName(String status) {
        return status == null ? "" : SAMPLE_STATUS.getOrDefault(status, status);
    }

    public static String bagStatusName(String status) {
        return status == null ? "" : BAG_STATUS.getOrDefault(status, status);
    }

    public static String scrapReasonName(String reason) {
        return reason == null ? "" : SCRAP_REASON.getOrDefault(reason, reason);
    }

    public static String reactionStatusName(String status) {
        return status == null ? "" : REACTION_STATUS.getOrDefault(status, status);
    }

    public static String reactionReviewName(String result) {
        return result == null ? "" : REACTION_REVIEW.getOrDefault(result, result);
    }

    public static String reactionLinkTypeName(String type) {
        return type == null ? "" : REACTION_LINK_TYPE.getOrDefault(type, type);
    }

    public static String reactionLinkStatusName(String status) {
        return status == null ? "" : REACTION_LINK_STATUS.getOrDefault(status, status);
    }

    public static String reactionActionName(String action) {
        return action == null ? "" : REACTION_LEDGER_ACTION.getOrDefault(action, action);
    }

    public static boolean isValidReactionReview(String result) {
        return result != null && REACTION_REVIEW.containsKey(result);
    }

    public static String urgencyName(String urgency) {
        return urgency == null ? "" : URGENCY.getOrDefault(urgency, urgency);
    }

    public static String requestStatusName(String status) {
        return status == null ? "" : REQUEST_STATUS.getOrDefault(status, status);
    }

    public static String matchResultName(String result) {
        return result == null ? "" : MATCH_RESULT.getOrDefault(result, result);
    }

    public static String testResultName(String result) {
        return result == null ? "" : TEST_RESULT.getOrDefault(result, result);
    }

    public static boolean isValidComponent(String component) {
        return component != null && COMPONENT_NAME.containsKey(component);
    }

    /** 配血结果三态，不含「未判定」 */
    public static boolean isValidMatchResult(String result) {
        return "COMPATIBLE".equals(result) || "SUSPECT".equals(result) || "INCOMPATIBLE".equals(result);
    }

    public static boolean isValidUrgency(String urgency) {
        return urgency != null && URGENCY.containsKey(urgency);
    }

    public static boolean isValidCollectionStatus(String status) {
        return status != null && COLLECTION_STATUS.containsKey(status);
    }

    public static boolean isValidSampleStatus(String status) {
        return status != null && SAMPLE_STATUS.containsKey(status);
    }

    public static boolean isValidTestItem(String itemCode) {
        return itemCode != null && TEST_ITEM_NAME.containsKey(itemCode);
    }

    public static String testItemName(String itemCode) {
        return itemCode == null ? "" : TEST_ITEM_NAME.getOrDefault(itemCode, itemCode);
    }

    /** ALT / 血红蛋白走数值结果，其余四项走阴阳性结果 */
    public static boolean isNumericTestItem(String itemCode) {
        return NUMERIC_TEST_ITEMS.contains(itemCode);
    }
}
