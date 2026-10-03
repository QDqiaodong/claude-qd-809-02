package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 采血记录视图。
 */
public record CollectionVO(Long id,
                           String collectNo,
                           Long donorId,
                           String donorName,
                           String bloodType,
                           String stationCode,
                           Integer volumeMl,
                           LocalDate collectDate,
                           String nurse,
                           String status,
                           String statusName) {
}
