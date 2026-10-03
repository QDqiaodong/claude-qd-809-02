package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 按血型汇总的制备成果（手写 JPQL 聚合结果）。
 */
public record PreparationSummaryVO(String bloodType,
                                   Long batchCount,
                                   Long totalBags,
                                   Long totalVolumeMl,
                                   LocalDateTime lastPreparedTime) {
}
