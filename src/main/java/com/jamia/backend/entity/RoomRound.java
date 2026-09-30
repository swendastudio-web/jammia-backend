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

import java.time.LocalDateTime;

/**
 * One round (rotation) of a room: every member receives once, one turn per period.
 * Finished rounds are kept as history.
 */
@Entity
@Table(name = "room_rounds")
public class RoomRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SavingsRoom room;

    // 1, 2, 3 ... per room
    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_order_method", nullable = false, length = 20)
    private TurnOrderMethod turnOrderMethod;

    // Turn 1 begins here.
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // The last turn ends here; then the round is completed.
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected RoomRound() {
    }

    public RoomRound(SavingsRoom room, int roundNumber, TurnOrderMethod turnOrderMethod,
                     LocalDateTime startedAt, LocalDateTime endsAt) {
        this.room = room;
        this.roundNumber = roundNumber;
        this.turnOrderMethod = turnOrderMethod;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.status = RoundStatus.ACTIVE;
    }

    public void complete(LocalDateTime now) {
        this.status = RoundStatus.COMPLETED;
        this.completedAt = now;
    }

    public Long getId() {
        return id;
    }

    public SavingsRoom getRoom() {
        return room;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public TurnOrderMethod getTurnOrderMethod() {
        return turnOrderMethod;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
