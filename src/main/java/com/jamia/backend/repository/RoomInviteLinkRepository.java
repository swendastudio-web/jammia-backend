package com.jamia.backend.repository;

import com.jamia.backend.entity.RoomInviteLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reads and saves members' invite links.
 */
public interface RoomInviteLinkRepository extends JpaRepository<RoomInviteLink, Long> {

    // Someone opened a link: load the room, its creator, and the member who shared the link.
    @EntityGraph(attributePaths = {"room", "room.creator", "createdBy", "createdBy.user"})
    Optional<RoomInviteLink> findByToken(String token);

    // A member's current (not yet expired) link for a room, so tapping "Invite" again reuses it.
    Optional<RoomInviteLink> findFirstByRoomIdAndCreatedByIdAndExpiresAtAfterOrderByExpiresAtDesc(
            Long roomId, Long memberId, LocalDateTime now);
}
