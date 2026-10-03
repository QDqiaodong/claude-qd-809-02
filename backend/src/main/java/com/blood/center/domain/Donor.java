package com.blood.center.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 献血者：血型、既往献血次数、累计献血量、暂缓献血截止日期。
 */
@Entity
@Table(name = "donor")
@Getter
@Setter
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_no", nullable = false, unique = true, length = 32)
    private String donorNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "gender", nullable = false, length = 8)
    private String gender;

    @Column(name = "age")
    private Integer age;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "id_card_no", nullable = false, unique = true, length = 32)
    private String idCardNo;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    /** 既往献血次数 */
    @Column(name = "donation_count")
    private Integer donationCount;

    /** 累计献血量 ml */
    @Column(name = "total_volume_ml")
    private Integer totalVolumeMl;

    /** 暂缓献血截止日期 */
    @Column(name = "defer_until")
    private LocalDate deferUntil;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
