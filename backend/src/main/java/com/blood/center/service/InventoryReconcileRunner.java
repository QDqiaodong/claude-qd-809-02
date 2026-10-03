package com.blood.center.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务启动后按血袋明细全量重算库存矩阵。
 * blood_inventory 是按血袋派生的汇总：任何历史漂移（如冷沉淀制备入库后未计入血浆格、
 * 种子数据与明细不一致、漏发同步）都在启动时被拉平，保证刷新 / 重启后矩阵、明细、记录一致。
 */
@Component
@Order(0)
public class InventoryReconcileRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconcileRunner.class);

    private final InventoryService inventoryService;

    public InventoryReconcileRunner(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int cells = inventoryService.reconcileAll();
        log.info("[启动校准] 已按可用血袋重算 {} 个血型×成分库存格（冷沉淀并入血浆格）", cells);
    }
}
