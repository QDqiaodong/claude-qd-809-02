package com.blood.center.event;

/**
 * 领域事件：采血完成 → 自动生成检验标本，标本进入「待检」轨道。
 */
public record CollectionCompletedEvent(Long collectionId, Long donorId) {
}
