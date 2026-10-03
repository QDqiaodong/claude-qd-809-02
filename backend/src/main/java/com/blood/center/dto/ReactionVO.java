package com.blood.center.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 输血不良反应处置事件视图。
 * detail 打开时携带完整来源链 trace、同源血袋清单 links、逐步处置台账 ledger。
 */
public record ReactionVO(Long id,
                         String eventNo,
                         Long matchId,
                         String matchNo,
                         String bagNo,
                         String recipient,
                         String hospital,
                         String reporter,
                         String symptoms,
                         LocalDateTime reactionTime,
                         LocalDateTime reportTime,
                         String status,
                         String statusName,
                         String reviewResult,
                         String reviewResultName,
                         String reviewer,
                         String reviewComment,
                         LocalDateTime reviewedAt,
                         Long sourceCollectionId,
                         int isolatedCount,
                         int releasedCount,
                         int scrappedCount,
                         int traceCount,
                         boolean duplicated,
                         String reportMessage,
                         List<ReactionLinkVO> links,
                         List<ReactionLedgerVO> ledger,
                         TraceChainVO trace) {

    /** 列表场景的精简构造（不含来源链 / 清单 / 台账） */
    public static ReactionVO summary(Long id, String eventNo, Long matchId, String matchNo, String bagNo,
                                     String recipient, String hospital, String reporter, String symptoms,
                                     LocalDateTime reactionTime, LocalDateTime reportTime, String status,
                                     String statusName, String reviewResult, String reviewResultName,
                                     String reviewer, String reviewComment, LocalDateTime reviewedAt,
                                     Long sourceCollectionId, int isolatedCount, int releasedCount,
                                     int scrappedCount, int traceCount) {
        return new ReactionVO(id, eventNo, matchId, matchNo, bagNo, recipient, hospital, reporter, symptoms,
                reactionTime, reportTime, status, statusName, reviewResult, reviewResultName, reviewer,
                reviewComment, reviewedAt, sourceCollectionId, isolatedCount, releasedCount, scrappedCount,
                traceCount, false, null, null, null, null);
    }
}
