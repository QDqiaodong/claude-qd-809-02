package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 医院用血申请视图。
 */
public record RequestVO(Long id,
                        String requestNo,
                        String hospital,
                        String patientName,
                        String patientBloodType,
                        String component,
                        String componentName,
                        Integer unitsU,
                        String urgency,
                        String urgencyName,
                        String status,
                        String statusName,
                        Integer issuedU,
                        Integer remainU,
                        LocalDateTime requestTime,
                        String remark) {
}
