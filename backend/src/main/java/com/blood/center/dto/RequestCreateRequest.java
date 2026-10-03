package com.blood.center.dto;

/**
 * 医院用血申请登记。
 */
public record RequestCreateRequest(String hospital,
                                   String patientName,
                                   String patientBloodType,
                                   String component,
                                   Integer unitsU,
                                   String urgency,
                                   String remark) {
}
