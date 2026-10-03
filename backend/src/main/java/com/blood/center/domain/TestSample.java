package com.blood.center.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.blood.center.event.SampleReleasedEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * 标本检验：条码、对应采血记录、ALT/血红蛋白/HBV/HCV/HIV/梅毒、阴阳性判定、复核人、状态。
 * 状态置为「已放行」时通过领域事件驱动「自动生成成分制备单 + 血袋入库」。
 */
@Entity
@Table(name = "test_sample")
@Getter
@Setter
public class TestSample {

    @Transient
    private final List<Object> events = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barcode", nullable = false, unique = true, length = 32)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private CollectionRecord collection;

    @Column(name = "donor_name", length = 64)
    private String donorName;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    /** ALT U/L */
    @Column(name = "alt_value", precision = 6, scale = 2)
    private BigDecimal altValue;

    /** 血红蛋白 g/L */
    @Column(name = "hb_value", precision = 5, scale = 1)
    private BigDecimal hbValue;

    @Column(name = "hbv_result", length = 8)
    private String hbvResult;

    @Column(name = "hcv_result", length = 8)
    private String hcvResult;

    @Column(name = "hiv_result", length = 8)
    private String hivResult;

    @Column(name = "syphilis_result", length = 8)
    private String syphilisResult;

    /** 复核人 */
    @Column(name = "reviewer", length = 32)
    private String reviewer;

    /** PENDING 待检 / TESTING 检测中 / RELEASED 已放行 / SCRAPPED 报废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "tested_at")
    private LocalDateTime testedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 放行：置为已放行并登记领域事件（监听器自动生成成分制备批次）
     */
    public void release(String reviewer) {
        this.reviewer = reviewer;
        this.status = "RELEASED";
        this.releasedAt = LocalDateTime.now();
        this.events.add(new SampleReleasedEvent(this.id, this.barcode,
                this.collection == null ? null : this.collection.getId(),
                this.bloodType,
                this.collection == null ? 0 : nullToZero(this.collection.getVolumeMl())));
    }

    public void scrap() {
        this.status = "SCRAPPED";
    }

    /** 献血者性别（血红蛋白判定标准不同） */
    public String donorGender() {
        if (collection == null || collection.getDonor() == null) {
            return "男";
        }
        return collection.getDonor().getGender();
    }

    private Integer nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    @DomainEvents
    public Collection<Object> domainEvents() {
        return Collections.unmodifiableList(events);
    }

    @AfterDomainEventPublication
    public void clearDomainEvents() {
        this.events.clear();
    }
}
