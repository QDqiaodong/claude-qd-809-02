package com.blood.center.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交叉配血 / 发血记录视图：左侧供血信息 + 右侧受血信息 + 配血结果。
 * blockReason 非空表示不可出库的原因（前端据此永久禁用出库按钮）。
 */
public record CrossMatchVO(Long id,
                           String matchNo,
                           Long requestId,
                           String requestNo,
                           Long bagId,
                           String bagNo,
                           String donorName,
                           String bloodType,
                           String component,
                           String componentName,
                           LocalDate expiryDate,
                           Integer daysToExpiry,
                           String recipient,
                           String hospital,
                           Integer unitsU,
                           String urgency,
                           String urgencyName,
                           String result,
                           String resultName,
                           String operator,
                           LocalDateTime matchTime,
                           Boolean issued,
                           LocalDateTime issueTime,
                           String issueOperator,
                           Boolean recoveryChecked,
                           LocalDateTime recoveryTime,
                           String sampleStatus,
                           String sampleStatusName,
                           String blockReason) {
}
