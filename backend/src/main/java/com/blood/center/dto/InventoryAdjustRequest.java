package com.blood.center.dto;

import java.time.LocalDate;

/**
 * 库存格子的出入库调整：mode=IN 入库 / OUT 出库。
 * expiryDate 仅入库时可指定（测试 / 补录近效期袋），为空则按成分保存期自动推算。
 */
public record InventoryAdjustRequest(String bloodType,
                                     String component,
                                     Integer bags,
                                     Integer volumeMl,
                                     LocalDate expiryDate,
                                     String mode,
                                     String operator,
                                     String remark) {
}
