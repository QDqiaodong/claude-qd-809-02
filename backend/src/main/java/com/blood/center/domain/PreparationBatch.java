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
 * 成分制备批次：全血来源，分离出红细胞悬液 / 血浆 / 血小板 / 冷沉淀 的袋数与容量。
 */
@Entity
@Table(name = "preparation_batch")
@Getter
@Setter
public class PreparationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 32)
    private String batchNo;

    /** 全血来源 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private CollectionRecord collection;

    @Column(name = "collect_no", length = 32)
    private String collectNo;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "rbc_bags")
    private Integer rbcBags;

    @Column(name = "rbc_volume_ml")
    private Integer rbcVolumeMl;

    @Column(name = "plasma_bags")
    private Integer plasmaBags;

    @Column(name = "plasma_volume_ml")
    private Integer plasmaVolumeMl;

    @Column(name = "platelet_bags")
    private Integer plateletBags;

    @Column(name = "platelet_volume_ml")
    private Integer plateletVolumeMl;

    @Column(name = "cryo_bags")
    private Integer cryoBags;

    @Column(name = "cryo_volume_ml")
    private Integer cryoVolumeMl;

    /** 制备人 */
    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "prepared_time")
    private LocalDateTime preparedTime;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
