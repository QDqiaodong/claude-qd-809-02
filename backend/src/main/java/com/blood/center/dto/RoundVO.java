package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 单个项目的一轮复测结果与复核处置。
 */
public record RoundVO(Long id,
                      String itemCode,
                      String itemName,
                      Integer roundNo,
                      String value,
                      String unit,
                      String conclusion,
                      String reason,
                      String operator,
                      LocalDateTime testedAt,
                      String decision,
                      String decisionName,
                      String decisionReason,
                      String reviewer,
                      LocalDateTime reviewedAt,
                      boolean current) {
}
