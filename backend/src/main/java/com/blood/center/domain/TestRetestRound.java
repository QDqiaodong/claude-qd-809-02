package com.blood.center.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 标本复测轮次：一轮只复测一个项目（ALT / 血红蛋白 / HBV / HCV / HIV / 梅毒）。
 * test_sample 上的初检结果视为第 0 轮，永不被覆盖；本表从第 1 轮开始追加。
 * 放行时复核人逐轮给出「采用 / 拒绝」及理由：
 *  - ADOPTED  本轮结果成为该项目最后一次有效结果；
 *  - REJECTED 本轮结果无效，沿用上一次有效轮（或初检）。
 */
@Entity
@Table(name = "test_retest_round")
@Getter
@Setter
public class TestRetestRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false)
    private TestSample sample;

    /** ALT / HB / HBV / HCV / HIV / SYPHILIS */
    @Column(name = "item_code", nullable = false, length = 16)
    private String itemCode;

    /** 该项目的复测轮次，初检为 0，第一次复测为 1 */
    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** 数值类项目（ALT / HB）的新结果 */
    @Column(name = "new_value", precision = 6, scale = 2)
    private BigDecimal newValue;

    /** 阴阳性项目（HBV / HCV / HIV / SYPHILIS）的新结果 NEGATIVE/POSITIVE */
    @Column(name = "new_result", length = 8)
    private String newResult;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "tested_at", nullable = false)
    private LocalDateTime testedAt;

    /** PENDING 待复核 / ADOPTED 采用 / REJECTED 拒绝 */
    @Column(name = "decision", nullable = false, length = 16)
    private String decision = "PENDING";

    @Column(name = "decision_reason", length = 512)
    private String decisionReason;

    @Column(name = "reviewer", length = 32)
    private String reviewer;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 复核人在放行时对本轮结果作出处置 */
    public void review(String decision, String decisionReason, String reviewer) {
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.reviewer = reviewer;
        this.reviewedAt = LocalDateTime.now();
    }
}
