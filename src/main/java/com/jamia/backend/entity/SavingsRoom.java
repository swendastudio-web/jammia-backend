package com.jamia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A savings room. Members contribute the same amount each period,
 * and one member receives the total each period.
 */
@Entity
@Table(name = "savings_rooms")
public class SavingsRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    // BigDecimal: exact money amounts (double would give rounding errors like 0.1 + 0.2).
    @Column(name = "contribution_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal contributionAmount;

    // 3-letter currency code, e.g. AED, OMR, USD.
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionFrequency frequency;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_order_method", length = 20)
    private TurnOrderMethod turnOrderMethod;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected SavingsRoom() {
    }

    public SavingsRoom(String name, String description, BigDecimal contributionAmount, String currency,
                       ContributionFrequency frequency, int maxMembers, User creator) {
        this.name = name;
        this.description = description;
        this.contributionAmount = contributionAmount;
        this.currency = currency;
        this.frequency = frequency;
        this.maxMembers = maxMembers;
        this.creator = creator;
        this.status = RoomStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Called when the creator starts the room: the turn order is now fixed.
    public void start(TurnOrderMethod turnOrderMethod, LocalDate startDate) {
        this.turnOrderMethod = turnOrderMethod;
        this.startDate = startDate;
        this.status = RoomStatus.ACTIVE;
    }

    public void complete() {
        this.status = RoomStatus.COMPLETED;
    }

    public boolean isCreatedBy(Long userId) {
        return creator.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getContributionAmount() {
        return contributionAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public ContributionFrequency getFrequency() {
        return frequency;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public User getCreator() {
        return creator;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public TurnOrderMethod getTurnOrderMethod() {
        return turnOrderMethod;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
