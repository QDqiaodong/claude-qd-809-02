package com.blood.center.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 血液报废记录：过期 / 破损 / 检验不合格。
 */
@Entity
@Table(name = "scrap_record")
@Getter
@Setter
public class ScrapRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scrap_no", nullable = false, unique = true, length = 32)
    private String scrapNo;

    @Column(name = "bag_no", length = 32)
    private String bagNo;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "component", nullable = false, length = 8)
    private String component;

    @Column(name = "bags")
    private Integer bags;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    /** EXPIRED 过期 / DAMAGED 破损 / FAILED 检验不合格 */
    @Column(name = "reason", nullable = false, length = 16)
    private String reason;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "scrap_time")
    private LocalDateTime scrapTime;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
