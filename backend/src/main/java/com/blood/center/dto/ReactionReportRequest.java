package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 医院上报输血不良反应：针对一条已发血记录，登记症状、发生时间、上报医院与处置人。
 */
public record ReactionReportRequest(Long matchId,
                                    String symptoms,
                                    LocalDateTime reactionTime,
                                    String hospital,
                                    String reporter) {
}
