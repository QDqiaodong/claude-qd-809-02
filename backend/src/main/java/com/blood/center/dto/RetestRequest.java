package com.blood.center.dto;

import java.math.BigDecimal;

/**
 * 发起复测请求：一轮只复测一个项目。
 * 数值型项目（ALT / HB）填 newValue，阴阳性项目（HBV / HCV / HIV / SYPHILIS）填 newResult。
 */
public record RetestRequest(String itemCode,
                            BigDecimal newValue,
                            String newResult,
                            String reason,
                            String operator) {
}
