package com.blood.center.dto;

import java.math.BigDecimal;

/**
 * 献血者建档。
 */
public record DonorRequest(String name,
                           String gender,
                           Integer age,
                           String bloodType,
                           String idCardNo,
                           String phone,
                           BigDecimal weightKg) {
}
