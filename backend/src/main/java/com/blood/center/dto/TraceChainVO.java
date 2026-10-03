package com.blood.center.dto;

/**
 * 一条发血记录反查的完整来源链：
 * 发血记录 → 供血血袋 → 制备批次 → 采血记录 → 检验标本。
 */
public record TraceChainVO(TraceBagVO.Issue issue,
                           TraceBagVO bag,
                           TraceBagVO.Batch batch,
                           TraceBagVO.Collection collection,
                           TraceBagVO.Sample sample,
                           String blockHint) {
}
