package com.jamia.backend.repository;

import com.jamia.backend.entity.RoomMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves room memberships.
 * @EntityGraph loads the related user (and room) in the same query, instead of one query per member.
 */
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // Members of a room: by turn (after the room starts), then by who joined first.
    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoomIdOrderByTurnPositionAscIdAsc(Long roomId);

    // The rooms a user belongs to, newest membership first.
    @EntityGraph(attributePaths = {"room", "room.creator"})
    List<RoomMember> findByUserIdOrderByJoinedAtDesc(Long userId);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);
}
