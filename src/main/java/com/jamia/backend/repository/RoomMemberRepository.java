package com.jamia.backend.repository;

import com.jamia.backend.entity.MemberStatus;
import com.jamia.backend.entity.RoomMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves room memberships. Most queries ask for ACTIVE members only;
 * members who LEFT or were REMOVED stay in the table for the payment history.
 * @EntityGraph loads the related user (and room) in the same query, instead of one query per member.
 */
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // Members of a room with a given status: by turn (during a round), then by who joined first.
    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoomIdAndStatusOrderByTurnPositionAscIdAsc(Long roomId, MemberStatus status);

    // The rooms a user belongs to (with a given status), newest membership first.
    @EntityGraph(attributePaths = {"room", "room.creator"})
    List<RoomMember> findByUserIdAndStatusOrderByJoinedAtDesc(Long userId, MemberStatus status);

    // Any membership, whatever its status (e.g. someone who left and is accepted again).
    @EntityGraph(attributePaths = {"room", "user"})
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserIdAndStatus(Long roomId, Long userId, MemberStatus status);

    long countByRoomIdAndStatus(Long roomId, MemberStatus status);

    // Used for photo privacy: are these two users members of at least one same room?
    @Query("""
            SELECT COUNT(a) > 0 FROM RoomMember a, RoomMember b
            WHERE a.room.id = b.room.id AND a.user.id = :userA AND b.user.id = :userB
              AND a.status = com.jamia.backend.entity.MemberStatus.ACTIVE
              AND b.status = com.jamia.backend.entity.MemberStatus.ACTIVE
            """)
    boolean shareARoom(@Param("userA") Long userA, @Param("userB") Long userB);
}
