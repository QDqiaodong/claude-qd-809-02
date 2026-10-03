package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 血袋视图（库存矩阵格子明细）。
 */
public record BagVO(Long id,
                    String bagNo,
                    String bloodType,
                    String component,
                    String componentName,
                    Integer volumeMl,
                    Integer units,
                    LocalDate collectDate,
                    LocalDate expiryDate,
                    Integer daysToExpiry,
                    String status,
                    String statusName,
                    String donorName,
                    String batchNo) {
}
