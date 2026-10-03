package com.blood.center.dto;

import java.math.BigDecimal;

/**
 * 检验结果录入请求：ALT / 血红蛋白 / HBV / HCV / HIV / 梅毒。
 */
public record TestResultRequest(BigDecimal altValue,
                                BigDecimal hbValue,
                                String hbvResult,
                                String hcvResult,
                                String hivResult,
                                String syphilisResult,
                                String operator) {
}
