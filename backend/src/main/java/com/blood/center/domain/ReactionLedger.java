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
 * 反应事件处置台账：上报、隔离、释放、报废、复核逐步留痕，只增不改。
 */
@Entity
@Table(name = "reaction_ledger")
@Getter
@Setter
public class ReactionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private TransfusionReaction event;

    @Column(name = "bag_id")
    private Long bagId;

    @Column(name = "bag_no", length = 32)
    private String bagNo;

    /** REPORT 上报 / ISOLATE 隔离 / RELEASE 释放 / SCRAP 报废 / REVIEW 复核 / KEEP 保持隔离 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "detail", nullable = false, length = 512)
    private String detail;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
