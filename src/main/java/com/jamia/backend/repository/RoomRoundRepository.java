package com.jamia.backend.repository;

import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.entity.RoundStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads and saves the rounds (rotations) of rooms.
 */
public interface RoomRoundRepository extends JpaRepository<RoomRound, Long> {

    // The newest round of a room (the running one, or the last finished one).
    Optional<RoomRound> findFirstByRoomIdOrderByRoundNumberDesc(Long roomId);

    Optional<RoomRound> findByRoomIdAndRoundNumber(Long roomId, int roundNumber);

    // History, newest first.
    List<RoomRound> findByRoomIdOrderByRoundNumberDesc(Long roomId);

    long countByRoomId(Long roomId);

    // Used by the clock: running rounds whose last turn has ended.
    @EntityGraph(attributePaths = "room")
    List<RoomRound> findByStatusAndEndsAtLessThanEqual(RoundStatus status, LocalDateTime now);
}
