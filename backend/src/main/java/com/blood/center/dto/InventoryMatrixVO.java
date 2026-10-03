package com.blood.center.dto;

import java.util.List;

/**
 * 血型 × 成分 二维库存矩阵。
 */
public record InventoryMatrixVO(List<String> bloodTypes,
                                List<ColumnVO> components,
                                List<InventoryCellVO> cells,
                                List<LegendVO> legend) {

    public record ColumnVO(String code, String name) {
    }

    /** 色带图例 */
    public record LegendVO(String level, String name, String color, String desc) {
    }
}
