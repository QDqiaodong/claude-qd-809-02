package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 按血型 × 成分汇总的库存（手写 JPQL 聚合结果）。
 */
public record ComponentSummaryVO(String bloodType,
                                 String component,
                                 String componentName,
                                 Long bagCount,
                                 Long unitCount,
                                 LocalDate nearestExpiry) {
}
