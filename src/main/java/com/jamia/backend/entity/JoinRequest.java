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
 * A person asking to join a room through a member's invite link.
 * The room creator sees who is asking AND which member referred them, then approves or rejects.
 */
@Entity
@Table(name = "join_requests")
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SavingsRoom room;

    // The person who wants to join.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The room member whose link was used.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_by_member_id", nullable = false)
    private RoomMember referredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected JoinRequest() {
    }

    public JoinRequest(SavingsRoom room, User user, RoomMember referredBy) {
        this.room = room;
        this.user = user;
        this.referredBy = referredBy;
        this.status = JoinRequestStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void approve() {
        this.status = JoinRequestStatus.APPROVED;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = JoinRequestStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
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

    public RoomMember getReferredBy() {
        return referredBy;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
