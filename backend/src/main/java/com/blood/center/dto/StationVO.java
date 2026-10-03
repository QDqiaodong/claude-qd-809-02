package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 采血位状态盘上的一个点位。
 */
public record StationVO(String code,
                        String status,
                        String statusName,
                        Long collectionId,
                        String donorName,
                        String donorSurname,
                        String bloodType,
                        Integer volumeMl,
                        String nurse,
                        String collectNo,
                        LocalDate collectDate) {
}
