package com.blood.center.event;

/**
 * 领域事件：血液过期 / 临近效期 → 自动生成报废记录，并从可用库存扣减。
 */
public record BloodExpiredEvent(Long bagId, String bagNo, String bloodType, String component,
                                Integer volumeMl, String reason) {
}
