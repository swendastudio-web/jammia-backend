package com.jamia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
