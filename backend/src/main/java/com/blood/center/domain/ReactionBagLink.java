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
 * 反应事件 × 同源血袋关联。
 * linkType：ISOLATED 建立事件时被隔离的在库血袋；TRACE 已发出 / 已报废血袋，只进入追溯清单，状态永不改写。
 * linkStatus：QUARANTINED 隔离中 / RELEASED 已释放 / SCRAPPED 已报废 / TRACED 仅追溯 / KEPT 复核后仍保持隔离。
 * 同一血袋可被多个事件隔离（uk_event_bag 仅约束事件内唯一）：任一事件未结束前都不得释放。
 */
@Entity
@Table(name = "reaction_bag_link")
@Getter
@Setter
public class ReactionBagLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private TransfusionReaction event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id")
    private BloodBag bag;

    @Column(name = "bag_no", nullable = false, length = 32)
    private String bagNo;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "component", nullable = false, length = 8)
    private String component;

    /** ISOLATED 隔离袋 / TRACE 仅追溯 */
    @Column(name = "link_type", nullable = false, length = 16)
    private String linkType;

    /** QUARANTINED / RELEASED / SCRAPPED / TRACED / KEPT */
    @Column(name = "link_status", nullable = false, length = 16)
    private String linkStatus;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
