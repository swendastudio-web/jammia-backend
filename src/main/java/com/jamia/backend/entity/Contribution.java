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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One member's payment for one cycle, to that cycle's recipient.
 * JAMIA does not move money: this only tracks what members report.
 */
@Entity
@Table(name = "contributions")
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SavingsRoom room;

    // Cycle 1 is paid to the member with turn 1, cycle 2 to turn 2, and so on.
    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_member_id", nullable = false)
    private RoomMember payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    private RoomMember recipient;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected Contribution() {
    }

    public Contribution(SavingsRoom room, int cycleNumber, LocalDate dueDate,
                        RoomMember payer, RoomMember recipient, BigDecimal amount) {
        this.room = room;
        this.cycleNumber = cycleNumber;
        this.dueDate = dueDate;
        this.payer = payer;
        this.recipient = recipient;
        this.amount = amount;
        this.status = ContributionStatus.PENDING;
    }

    // The payer says "I paid".
    public void markPaid() {
        this.status = ContributionStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    // The recipient says "I received it".
    public void confirm() {
        this.status = ContributionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SavingsRoom getRoom() {
        return room;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public RoomMember getPayer() {
        return payer;
    }

    public RoomMember getRecipient() {
        return recipient;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ContributionStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
