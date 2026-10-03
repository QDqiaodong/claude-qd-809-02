package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 库存矩阵中的一格：血型 × 成分。
 * level：OK 正常 / WARN 低于预警线 / LOW 严重不足 / EXPIRED 最近效期已过或 3 天内到期。
 */
public record InventoryCellVO(String bloodType,
                              String component,
                              String componentName,
                              Integer bagCount,
                              Integer unitCount,
                              LocalDate nearestExpiry,
                              Integer warningLine,
                              Integer daysToExpiry,
                              String level) {
}
