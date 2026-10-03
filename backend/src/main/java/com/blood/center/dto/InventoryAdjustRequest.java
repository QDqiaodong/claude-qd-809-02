package com.blood.center.dto;

/**
 * 库存格子的出入库调整：mode=IN 入库 / OUT 出库。
 */
public record InventoryAdjustRequest(String bloodType,
                                     String component,
                                     Integer bags,
                                     Integer volumeMl,
                                     String mode,
                                     String operator,
                                     String remark) {
}
