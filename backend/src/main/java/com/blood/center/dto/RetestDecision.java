package com.blood.center.dto;

/**
 * 放行时复核人对某一复测轮的处置：采用 / 拒绝 + 理由（理由必填）。
 */
public record RetestDecision(Long roundId,
                             String decision,
                             String reason) {
}
