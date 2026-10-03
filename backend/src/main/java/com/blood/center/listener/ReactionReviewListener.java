package com.blood.center.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blood.center.event.ReactionReviewedEvent;
import com.blood.center.service.ReactionService;

/**
 * 复核提交后补扫：并发两宗「排除关联」同时结束时，
 * 最后提交的一方在新事务里把残留的隔离血袋重新评估并释放，避免永远锁在隔离态。
 */
@Component
public class ReactionReviewListener {

    private static final Logger log = LoggerFactory.getLogger(ReactionReviewListener.class);

    private final ReactionService reactionService;

    public ReactionReviewListener(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewed(ReactionReviewedEvent event) {
        reactionService.sweepOrphanQuarantines();
        log.info("[复核补扫] 事件 {} 复核结论 {}，已重新评估残留同源隔离", event.eventId(), event.result());
    }
}
