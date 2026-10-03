package com.blood.center.event;

/**
 * 领域事件：发血出库 → 扣减「血型 × 成分」库存、记录发血流水并回写用血申请。
 */
public record BloodIssuedEvent(Long matchId, String matchNo, String bloodType, String component,
                               Integer unitsU, Long bagId, Long requestId) {
}
