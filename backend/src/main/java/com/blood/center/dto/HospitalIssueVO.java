package com.blood.center.dto;

/**
 * 按医院汇总发血量（手写 JPQL 聚合结果）。
 */
public record HospitalIssueVO(String hospital,
                              Long matchCount,
                              Long issuedUnits) {
}
