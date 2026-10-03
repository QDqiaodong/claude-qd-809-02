package com.blood.center.domain;

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
 * 输血不良反应处置事件：医院针对一条已发血记录上报，
 * 登记症状、发生时间、上报医院和处置人，并反查整条来源链。
 * 同一发血记录在反应时间和症状相同时，重复上报 / 双人同时上报只形成一宗事件（dedupKey 唯一）。
 * 状态：OPEN 在办 / EXCLUDED 排除关联 / CONFIRMED 确认风险。
 */
@Entity
@Table(name = "transfusion_reaction")
@Getter
@Setter
public class TransfusionReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, unique = true, length = 32)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private CrossMatch match;

    @Column(name = "match_no", nullable = false, length = 32)
    private String matchNo;

    @Column(name = "bag_no", length = 32)
    private String bagNo;

    @Column(name = "recipient", nullable = false, length = 64)
    private String recipient;

    @Column(name = "hospital", nullable = false, length = 128)
    private String hospital;

    /** 上报人 / 处置人 */
    @Column(name = "reporter", nullable = false, length = 64)
    private String reporter;

    @Column(name = "symptoms", nullable = false, length = 512)
    private String symptoms;

    @Column(name = "reaction_time", nullable = false)
    private LocalDateTime reactionTime;

    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    @Column(name = "source_collection_id")
    private Long sourceCollectionId;

    /** OPEN 在办 / EXCLUDED 排除关联 / CONFIRMED 确认风险 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "review_result", length = 16)
    private String reviewResult;

    @Column(name = "reviewer", length = 64)
    private String reviewer;

    @Column(name = "review_comment", length = 512)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 幂等键：发血记录 + 反应时间（分钟） + 症状 的 SHA-256，唯一约束保证并发只落一宗 */
    @Column(name = "dedup_key", nullable = false, unique = true, length = 64)
    private String dedupKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public boolean isOpen() {
        return "OPEN".equals(this.status);
    }
}
