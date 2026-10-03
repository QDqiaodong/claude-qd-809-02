package com.blood.center.event;

/**
 * 质量复核落结论并提交后触发：补扫并发两宗「排除关联」同时结束时可能残留的隔离血袋。
 */
public record ReactionReviewedEvent(Long eventId, String result) {
}
