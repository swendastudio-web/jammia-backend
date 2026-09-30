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

import java.time.LocalDateTime;

/**
 * A user's membership in a savings room, with their turn number.
 */
@Entity
@Table(name = "room_members")
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SavingsRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 1 = receives the total in the first cycle. Empty until the room starts.
    @Column(name = "turn_position")
    private Integer turnPosition;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    // ACTIVE, LEFT or REMOVED. Members are never deleted, so their payment history stays correct.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected RoomMember() {
    }

    public RoomMember(SavingsRoom room, User user) {
        this.room = room;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SavingsRoom getRoom() {
        return room;
    }

    public User getUser() {
        return user;
    }

    public Integer getTurnPosition() {
        return turnPosition;
    }

    public void setTurnPosition(Integer turnPosition) {
        this.turnPosition = turnPosition;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    // The member leaves, or the admin removes them. Their turn is cleared.
    public void end(MemberStatus newStatus) {
        this.status = newStatus;
        this.leftAt = LocalDateTime.now();
        this.turnPosition = null;
    }

    // Someone who left earlier is accepted into the room again.
    public void rejoin() {
        this.status = MemberStatus.ACTIVE;
        this.leftAt = null;
    }
}
