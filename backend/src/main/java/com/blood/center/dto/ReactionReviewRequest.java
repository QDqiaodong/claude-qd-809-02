package com.blood.center.dto;

/**
 * 质量复核结论：EXCLUDED 排除关联 / CONFIRMED 确认风险。
 */
public record ReactionReviewRequest(String result,
                                    String reviewer,
                                    String comment) {
}
