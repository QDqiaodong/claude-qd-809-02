package com.blood.center.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.blood.center.event.BloodIssuedEvent;

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
 * 交叉配血记录：供血袋号、受血者、配血结果（相合/可疑/不相合），以及发血与回收核对。
 * 出库时登记领域事件「发血出库」，由监听器扣减库存并记录发血流水。
 */
@Entity
@Table(name = "cross_match")
@Getter
@Setter
public class CrossMatch {

    @Transient
    private final List<Object> events = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_no", nullable = false, unique = true, length = 32)
    private String matchNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private HospitalRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id")
    private BloodBag bag;

    @Column(name = "bag_no", length = 32)
    private String bagNo;

    /** 供血者 */
    @Column(name = "donor_name", length = 64)
    private String donorName;

    @Column(name = "blood_type", nullable = false, length = 4)
    private String bloodType;

    @Column(name = "component", nullable = false, length = 8)
    private String component;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 受血者 */
    @Column(name = "recipient", nullable = false, length = 64)
    private String recipient;

    @Column(name = "hospital", length = 128)
    private String hospital;

    @Column(name = "units_u")
    private Integer unitsU;

    @Column(name = "urgency", length = 16)
    private String urgency;

    /** COMPATIBLE 相合 / SUSPECT 可疑 / INCOMPATIBLE 不相合 */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "match_time")
    private LocalDateTime matchTime;

    @Column(name = "issued")
    private Boolean issued;

    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    @Column(name = "issue_operator", length = 32)
    private String issueOperator;

    /** 血袋回收核对 */
    @Column(name = "recovery_checked")
    private Boolean recoveryChecked;

    @Column(name = "recovery_time")
    private LocalDateTime recoveryTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 发血出库：置为已出库并登记领域事件（监听器扣减库存、记录发血流水）
     */
    public void issue(String operator) {
        this.issued = Boolean.TRUE;
        this.issueTime = LocalDateTime.now();
        this.issueOperator = operator;
        this.events.add(new BloodIssuedEvent(this.id, this.matchNo, this.bloodType, this.component,
                this.unitsU == null ? 0 : this.unitsU,
                this.bag == null ? null : this.bag.getId(),
                this.request == null ? null : this.request.getId()));
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
