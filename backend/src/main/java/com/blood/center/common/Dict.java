package com.blood.center.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门店词典与业务规则参数表（职级门槛、工位类型上限、最低价、折扣等）。
 * 规则集中在此，便于出题时被要求调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 技师职级 ----------------
    private static final List<String> LEVEL_ORDER = List.of("JUNIOR", "SENIOR", "EXPERT", "DIRECTOR");

    private static final Map<String, String> LEVEL_NAME = Map.of(
            "JUNIOR", "见习技师",
            "SENIOR", "技师",
            "EXPERT", "高级技师",
            "DIRECTOR", "总监");

    /** 职级最低从业年限门槛 */
    private static final Map<String, Integer> LEVEL_MIN_YEARS = Map.of(
            "JUNIOR", 0,
            "SENIOR", 1,
            "EXPERT", 3,
            "DIRECTOR", 5);

    /** 职级允许的最高提成比例（%） */
    private static final Map<String, Integer> LEVEL_MAX_COMMISSION = Map.of(
            "JUNIOR", 10,
            "SENIOR", 20,
            "EXPERT", 30,
            "DIRECTOR", 40);

    /** 技师最多可登记几个擅长项目 */
    public static final int MAX_SPECIALTIES = 3;

    // ---------------- 技师状态 ----------------
    private static final Map<String, String> STYLIST_STATUS = Map.of(
            "ACTIVE", "在岗",
            "RESTING", "休息",
            "RESIGNED", "离职");

    // ---------------- 工位类型 ----------------
    private static final Map<String, String> STATION_TYPE = Map.of(
            "WASH_BED", "洗头床",
            "CUT_SEAT", "剪发位",
            "COLOR_SEAT", "烫染位");

    /** 各类工位数量上限 */
    private static final Map<String, Integer> STATION_LIMIT = Map.of(
            "WASH_BED", 4,
            "CUT_SEAT", 6,
            "COLOR_SEAT", 4);

    /** 各类工位每日可用时长下限（小时） */
    private static final Map<String, BigDecimal> STATION_MIN_HOURS = Map.of(
            "WASH_BED", BigDecimal.valueOf(8),
            "CUT_SEAT", BigDecimal.valueOf(9),
            "COLOR_SEAT", BigDecimal.valueOf(10));

    /** 工位编号前缀 */
    private static final Map<String, String> STATION_PREFIX = Map.of(
            "WASH_BED", "W",
            "CUT_SEAT", "C",
            "COLOR_SEAT", "T");

    private static final Map<String, String> STATION_STATUS = Map.of(
            "IDLE", "空闲",
            "OCCUPIED", "占用",
            "MAINTENANCE", "维护中");

    // ---------------- 服务类别 ----------------
    private static final Map<String, String> CATEGORY = Map.of(
            "WASH", "洗护",
            "CUT", "剪发",
            "PERM", "烫发",
            "COLOR", "染发",
            "CARE", "护理");

    /** 服务编号前缀：类别字母三位 + 序号 */
    private static final Map<String, String> CATEGORY_PREFIX = Map.of(
            "WASH", "WSH",
            "CUT", "CUT",
            "PERM", "PRM",
            "COLOR", "CLR",
            "CARE", "CAR");

    /** 上架服务的最低指导价 */
    private static final Map<String, BigDecimal> CATEGORY_MIN_PRICE = Map.of(
            "WASH", BigDecimal.valueOf(38),
            "CUT", BigDecimal.valueOf(68),
            "PERM", BigDecimal.valueOf(298),
            "COLOR", BigDecimal.valueOf(258),
            "CARE", BigDecimal.valueOf(98));

    /** 单服务时长上限（分钟） */
    private static final Map<String, Integer> CATEGORY_MAX_DURATION = Map.of(
            "WASH", 60,
            "CUT", 90,
            "PERM", 300,
            "COLOR", 300,
            "CARE", 150);

    private static final Map<String, String> SERVICE_STATUS = Map.of(
            "ON", "上架",
            "OFF", "下架");

    private static final Map<String, String> APPOINTMENT_STATUS = Map.of(
            "PENDING", "待到店",
            "DONE", "已完成",
            "CANCELED", "已取消");

    // ---------------- 会员等级 ----------------
    private static final List<String> MEMBER_LEVEL_ORDER = List.of("NORMAL", "SILVER", "GOLD", "DIAMOND");

    private static final Map<String, String> MEMBER_LEVEL = Map.of(
            "NORMAL", "普通会员",
            "SILVER", "银卡会员",
            "GOLD", "金卡会员",
            "DIAMOND", "钻石卡会员");

    /** 会员等级对应折扣率 */
    private static final Map<String, BigDecimal> MEMBER_DISCOUNT = Map.of(
            "NORMAL", BigDecimal.valueOf(1.00),
            "SILVER", BigDecimal.valueOf(0.95),
            "GOLD", BigDecimal.valueOf(0.90),
            "DIAMOND", BigDecimal.valueOf(0.85));

    /** 累计充值达到此金额可升级对应等级（只升不降） */
    private static final Map<String, BigDecimal> MEMBER_UPGRADE_THRESHOLD = Map.of(
            "SILVER", BigDecimal.valueOf(3000),
            "GOLD", BigDecimal.valueOf(10000),
            "DIAMOND", BigDecimal.valueOf(20000));

    private static final Map<String, String> CARD_STATUS = Map.of(
            "NORMAL", "正常",
            "FROZEN", "冻结");

    // ---------------- 支付方式 ----------------
    private static final Map<String, String> PAY_MODE = Map.of(
            "BALANCE", "储值余额",
            "CASH", "现金",
            "WECHAT", "微信支付",
            "CARD", "银行卡");

    // ---------------- 营业时间 ----------------
    public static final String BUSINESS_OPEN = "10:00";
    public static final String BUSINESS_CLOSE = "22:00";
    public static final BigDecimal SINGLE_RECHARGE_MIN = BigDecimal.valueOf(100);
    public static final BigDecimal SINGLE_RECHARGE_MAX = BigDecimal.valueOf(5000);

    // ---------------- 取值方法 ----------------

    public static boolean isValidLevel(String level) {
        return level != null && LEVEL_NAME.containsKey(level);
    }

    public static String levelName(String level) {
        return level == null ? "" : LEVEL_NAME.getOrDefault(level, level);
    }

    public static int levelRank(String level) {
        int index = LEVEL_ORDER.indexOf(level);
        return index < 0 ? 0 : index + 1;
    }

    public static int levelMinYears(String level) {
        return LEVEL_MIN_YEARS.getOrDefault(level, 0);
    }

    public static int levelMaxCommission(String level) {
        return LEVEL_MAX_COMMISSION.getOrDefault(level, 10);
    }

    public static boolean isValidStylistStatus(String status) {
        return status != null && STYLIST_STATUS.containsKey(status);
    }

    public static String stylistStatusName(String status) {
        return status == null ? "" : STYLIST_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidStationType(String type) {
        return type != null && STATION_TYPE.containsKey(type);
    }

    public static String stationTypeName(String type) {
        return type == null ? "" : STATION_TYPE.getOrDefault(type, type);
    }

    public static int stationLimit(String type) {
        return STATION_LIMIT.getOrDefault(type, 0);
    }

    public static BigDecimal stationMinHours(String type) {
        return STATION_MIN_HOURS.getOrDefault(type, BigDecimal.ZERO);
    }

    public static String stationPrefix(String type) {
        return STATION_PREFIX.getOrDefault(type, "");
    }

    public static boolean isValidStationStatus(String status) {
        return status != null && STATION_STATUS.containsKey(status);
    }

    public static String stationStatusName(String status) {
        return status == null ? "" : STATION_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCategory(String category) {
        return category != null && CATEGORY.containsKey(category);
    }

    public static String categoryName(String category) {
        return category == null ? "" : CATEGORY.getOrDefault(category, category);
    }

    public static String categoryPrefix(String category) {
        return CATEGORY_PREFIX.getOrDefault(category, "");
    }

    public static BigDecimal categoryMinPrice(String category) {
        return CATEGORY_MIN_PRICE.getOrDefault(category, BigDecimal.ZERO);
    }

    public static int categoryMaxDuration(String category) {
        return CATEGORY_MAX_DURATION.getOrDefault(category, 240);
    }

    public static boolean isValidServiceStatus(String status) {
        return status != null && SERVICE_STATUS.containsKey(status);
    }

    public static String serviceStatusName(String status) {
        return status == null ? "" : SERVICE_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidAppointmentStatus(String status) {
        return status != null && APPOINTMENT_STATUS.containsKey(status);
    }

    public static String appointmentStatusName(String status) {
        return status == null ? "" : APPOINTMENT_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVEL.containsKey(level);
    }

    public static String memberLevelName(String level) {
        return level == null ? "" : MEMBER_LEVEL.getOrDefault(level, level);
    }

    public static BigDecimal memberDiscount(String level) {
        return MEMBER_DISCOUNT.getOrDefault(level, BigDecimal.valueOf(1.00));
    }

    public static int memberLevelRank(String level) {
        int index = MEMBER_LEVEL_ORDER.indexOf(level);
        return index < 0 ? 0 : index + 1;
    }

    /**
     * 依据累计充值金额算出应得的最高等级
     */
    public static String levelByTotalRecharge(BigDecimal totalRecharge) {
        BigDecimal total = totalRecharge == null ? BigDecimal.ZERO : totalRecharge;
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("DIAMOND")) >= 0) {
            return "DIAMOND";
        }
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("GOLD")) >= 0) {
            return "GOLD";
        }
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("SILVER")) >= 0) {
            return "SILVER";
        }
        return "NORMAL";
    }

    public static boolean isValidCardStatus(String status) {
        return status != null && CARD_STATUS.containsKey(status);
    }

    public static String cardStatusName(String status) {
        return status == null ? "" : CARD_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidPayMode(String mode) {
        return mode != null && PAY_MODE.containsKey(mode);
    }

    public static String payModeName(String mode) {
        return mode == null ? "" : PAY_MODE.getOrDefault(mode, mode);
    }
}
