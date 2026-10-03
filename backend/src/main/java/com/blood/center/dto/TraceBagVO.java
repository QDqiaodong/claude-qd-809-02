package com.blood.center.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 来源链上的供血血袋（即触发反应的那袋已发出血液）。
 */
public record TraceBagVO(Long id,
                         String bagNo,
                         String bloodType,
                         String component,
                         String componentName,
                         Integer volumeMl,
                         LocalDate collectDate,
                         LocalDate expiryDate,
                         String status,
                         String statusName,
                         String donorName,
                         String batchNo) {

    /** 制备批次 */
    public record Batch(Long id,
                        String batchNo,
                        String collectNo,
                        String bloodType,
                        Integer rbcBags,
                        Integer plasmaBags,
                        Integer plateletBags,
                        Integer cryoBags,
                        String operator,
                        LocalDateTime preparedTime,
                        String status) {
    }

    /** 采血记录 */
    public record Collection(Long id,
                             String collectNo,
                             String donorNo,
                             String donorName,
                             String donorGender,
                             Integer donorAge,
                             String bloodType,
                             String stationCode,
                             Integer volumeMl,
                             LocalDate collectDate,
                             String nurse,
                             String status,
                             String statusName) {
    }

    /** 检验标本 */
    public record Sample(Long id,
                         String barcode,
                         String bloodType,
                         String altValue,
                         String hbValue,
                         String hbvResult,
                         String hcvResult,
                         String hivResult,
                         String syphilisResult,
                         String reviewer,
                         String status,
                         String statusName,
                         LocalDateTime testedAt,
                         LocalDateTime releasedAt) {
    }

    /** 发血记录（上报入口） */
    public record Issue(Long id,
                        String matchNo,
                        String requestNo,
                        String bagNo,
                        String donorName,
                        String bloodType,
                        String component,
                        String componentName,
                        String recipient,
                        String hospital,
                        Integer unitsU,
                        String urgency,
                        String urgencyName,
                        String result,
                        String resultName,
                        String issueOperator,
                        LocalDateTime issueTime,
                        Boolean recoveryChecked) {
    }
}
