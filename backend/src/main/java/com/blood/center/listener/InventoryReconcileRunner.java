package com.blood.center.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.blood.center.service.InventoryService;

/**
 * 启动对账：服务重启后以 blood_bag 可用血袋为准，全量重建「血型 × 成分」库存矩阵汇总。
 *
 * 矩阵格（blood_inventory 的袋数 / U 数 / 最近效期）只是血袋明细（blood_bag）的物化汇总，
 * 不允许两者漂移：旧版本把冷沉淀并入血浆格、历史种子数据手算误差、手工改库等情况，
 * 都在启动时按血袋事实纠正；之后所有出入库 / 发血 / 报废 / 隔离路径仍逐格增量重算。
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
    public void run(ApplicationArguments args) {
        int cells = inventoryService.reconcileAll();
        log.info("[启动对账] 已按可用血袋重建 {} 个库存格（8 血型 × 5 成分），矩阵与明细口径对齐", cells);
    }
}
