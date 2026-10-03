package com.blood.center.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 血液库存：按「血型 × 成分」二维管理，记录现有袋数、库存 U 数、最近效期与预警线。
 */
@Entity
@Table(name = "blood_inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_cell", columnNames = {"blood_type", "component"})
})
@Getter
@Setter
public class BloodInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    /** WHOLE 全血 / RBC 红细胞 / PLASMA 血浆 / PLATELET 血小板 */
    @Column(name = "component", nullable = false, length = 8)
    private String component;

    /** 现有袋数 */
    @Column(name = "bag_count")
    private Integer bagCount;

    /** 库存 U 数 */
    @Column(name = "unit_count")
    private Integer unitCount;

    /** 最近效期 */
    @Column(name = "nearest_expiry")
    private LocalDate nearestExpiry;

    /** 预警线（袋） */
    @Column(name = "warning_line")
    private Integer warningLine;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
