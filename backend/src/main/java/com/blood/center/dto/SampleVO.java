package com.blood.center.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标本检验视图：6 个检验项目当前有效结果 / 阴阳性判定 / 复核人 / 状态。
 * 扁平字段（altValue 等）始终表示「当前判定所依据的有效结果」：
 * 初检后=初检值；有复测时=各项目最后一次有效（被采用）轮次的值。
 * rounds 为按复测轮次展开的完整历史，原结果不覆盖。
 */
public record SampleVO(Long id,
                       String barcode,
                       Long collectionId,
                       String collectNo,
                       String donorName,
                       String bloodType,
                       BigDecimal altValue,
                       BigDecimal hbValue,
                       String hbvResult,
                       String hcvResult,
                       String hivResult,
                       String syphilisResult,
                       String reviewer,
                       String status,
                       String statusName,
                       LocalDateTime testedAt,
                       LocalDateTime releasedAt,
                       List<ItemVO> items,
                       List<RoundVO> rounds,
                       long retestRoundCount,
                       long pendingRoundCount) {

    /**
     * 单个检验项目的当前结果展示（ALT / 血红蛋白 / HBV / HCV / HIV / 梅毒）。
     * roundNo=0 表示当前依据初检；&gt;0 表示当前依据第 N 轮复测。
     */
    public record ItemVO(String code, String name, String value, String unit, String conclusion,
                         int roundNo, boolean retested, boolean pending) {
    }
}
