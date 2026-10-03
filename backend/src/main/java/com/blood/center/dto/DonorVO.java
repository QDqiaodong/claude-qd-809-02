package com.blood.center.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 献血者视图：附带能否献血的判断结果（下次可献日期 / 体重允许的采血上限）。
 */
public record DonorVO(Long id,
                      String donorNo,
                      String name,
                      String gender,
                      Integer age,
                      String bloodType,
                      String idCardNo,
                      String phone,
                      BigDecimal weightKg,
                      Integer donationCount,
                      Integer totalVolumeMl,
                      LocalDate deferUntil,
                      LocalDate lastDonationDate,
                      LocalDate nextAvailableDate,
                      Integer maxVolumeMl,
                      Boolean collectable,
                      String blockReason) {
}
