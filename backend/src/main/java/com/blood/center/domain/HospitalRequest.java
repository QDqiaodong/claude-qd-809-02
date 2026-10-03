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
 * 医院用血申请：医院、患者血型、申请成分与 U 数、紧急程度。
 */
@Entity
@Table(name = "hospital_request")
@Getter
@Setter
public class HospitalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, unique = true, length = 32)
    private String requestNo;

    @Column(name = "hospital", nullable = false, length = 128)
    private String hospital;

    @Column(name = "patient_name", nullable = false, length = 64)
    private String patientName;

    @Column(name = "patient_blood_type", nullable = false, length = 4)
    private String patientBloodType;

    @Column(name = "component", nullable = false, length = 8)
    private String component;

    /** 申请 U 数 */
    @Column(name = "units_u")
    private Integer unitsU;

    /** ROUTINE 常规 / URGENT 紧急 / CRITICAL 特急 */
    @Column(name = "urgency", nullable = false, length = 16)
    private String urgency;

    /** PENDING 待发血 / PARTIAL 部分发放 / DONE 已发放 / CANCELED 已取消 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 已发 U 数 */
    @Column(name = "issued_u")
    private Integer issuedU;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
