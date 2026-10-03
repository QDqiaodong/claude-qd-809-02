package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 处置台账条目：REPORT 上报 / ISOLATE 隔离 / RELEASE 释放 / SCRAP 报废 / REVIEW 复核 / KEEP 保持隔离。
 */
public record ReactionLedgerVO(Long id,
                               Long bagId,
                               String bagNo,
                               String action,
                               String actionName,
                               String detail,
                               String operator,
                               LocalDateTime actionTime) {
}
