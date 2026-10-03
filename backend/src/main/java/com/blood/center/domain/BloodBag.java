package com.blood.center.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.blood.center.event.BloodExpiredEvent;

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
 * 血袋：袋号、血型、成分、容量、失效日期、状态。
 * 判为过期时通过领域事件驱动「自动生成报废记录 + 从可用库存扣减」。
 */
@Entity
@Table(name = "blood_bag")
@Getter
@Setter
public class BloodBag {

    @Transient
    private final List<Object> events = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bag_no", nullable = false, unique = true, length = 32)
    private String bagNo;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "component", nullable = false, length = 8)
    private String component;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    @Column(name = "collect_date")
    private LocalDate collectDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** AVAILABLE 可用 / ISSUED 已发出 / SCRAPPED 已报废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "donor_name", length = 64)
    private String donorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private PreparationBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private CollectionRecord collection;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 判为过期：置为已报废并登记领域事件（监听器生成报废记录并扣减库存）
     */
    public void markExpired(String reason) {
        this.status = "SCRAPPED";
        this.events.add(new BloodExpiredEvent(this.id, this.bagNo, this.bloodType, this.component,
                this.volumeMl == null ? 0 : this.volumeMl, reason));
    }

    public void markIssued() {
        this.status = "ISSUED";
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
