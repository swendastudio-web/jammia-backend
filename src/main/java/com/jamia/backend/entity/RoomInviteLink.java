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
 * A member's personal invite link for a room.
 * People who use it are "referred by" this member; the room creator still approves them.
 */
@Entity
@Table(name = "room_invite_links")
public class RoomInviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SavingsRoom room;

    // The member who shares this link (the referrer).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private RoomMember createdBy;

    // The secret, hard-to-guess part of the link: https://<domain>/invite/<token>
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // JPA needs an empty constructor to create objects from database rows.
    protected RoomInviteLink() {
    }

    public RoomInviteLink(SavingsRoom room, RoomMember createdBy, String token, LocalDateTime expiresAt) {
        this.room = room;
        this.createdBy = createdBy;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public SavingsRoom getRoom() {
        return room;
    }

    public RoomMember getCreatedBy() {
        return createdBy;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
