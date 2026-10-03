package com.blood.center.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.blood.center.event.CollectionCompletedEvent;

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
 * 采血记录：采血位、采血量、采血日期、采血护士、状态。
 * 采血完成（DONE）时通过领域事件驱动「自动生成检验标本」。
 */
@Entity
@Table(name = "collection_record")
@Getter
@Setter
public class CollectionRecord {

    @Transient
    private final List<Object> events = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collect_no", nullable = false, unique = true, length = 32)
    private String collectNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private Donor donor;

    @Column(name = "station_code", nullable = false, length = 8)
    private String stationCode;

    /** 采血量 ml */
    @Column(name = "volume_ml")
    private Integer volumeMl;

    @Column(name = "collect_date")
    private LocalDate collectDate;

    /** 采血护士 */
    @Column(name = "nurse", length = 32)
    private String nurse;

    /** COLLECTING 采血中 / DONE 已完成 / ABNORMAL 异常 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 采血完成：置为已完成并登记领域事件（由监听器生成检验标本）
     */
    public void complete() {
        this.status = "DONE";
        this.events.add(new CollectionCompletedEvent(this.id, this.donor == null ? null : this.donor.getId()));
    }

    public void abnormal() {
        this.status = "ABNORMAL";
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
