package com.blood.center.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blood.center.common.ApiResponse;
import com.blood.center.dto.BagVO;
import com.blood.center.dto.ComponentSummaryVO;
import com.blood.center.dto.InventoryAdjustRequest;
import com.blood.center.dto.InventoryCellVO;
import com.blood.center.dto.InventoryMatrixVO;
import com.blood.center.dto.ScrapVO;
import com.blood.center.service.InventoryService;

/**
 * 模块四：血液库存（血型 × 成分 二维矩阵、格子明细、出入库、报废）。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 二维库存矩阵 */
    @GetMapping("/matrix")
    public ApiResponse<InventoryMatrixVO> matrix(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(inventoryService.matrix(bloodType));
    }

    /** 矩阵一格里的血袋明细 */
    @GetMapping("/cells")
    public ApiResponse<List<BagVO>> cellBags(@RequestParam String bloodType, @RequestParam String component) {
        return ApiResponse.success(inventoryService.cellBags(bloodType, component));
    }

    /** 格子出入库 */
    @PostMapping("/adjust")
    public ApiResponse<InventoryCellVO> adjust(@RequestBody InventoryAdjustRequest request) {
        return ApiResponse.success("库存已调整", inventoryService.adjust(request));
    }

    /** 过期扫描：过期血袋自动生成报废记录并从库存扣减 */
    @PostMapping("/scrap-expired")
    public ApiResponse<Map<String, Object>> scrapExpired() {
        int count = inventoryService.scrapExpired();
        return ApiResponse.success("过期扫描完成，判废 " + count + " 袋并已生成报废记录",
                Map.of("scrapped", count));
    }

    /** 报废记录 */
    @GetMapping("/scraps")
    public ApiResponse<List<ScrapVO>> scraps(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(inventoryService.listScraps(bloodType));
    }

    /** 按血型 × 成分汇总库存（JPQL） */
    @GetMapping("/summary")
    public ApiResponse<List<ComponentSummaryVO>> summary() {
        return ApiResponse.success(inventoryService.summary());
    }
}
