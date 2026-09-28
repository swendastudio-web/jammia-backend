package com.jamia.backend.repository;

import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves contributions (payment tracking).
 */
public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // All contributions of a room, by cycle. Payer and recipient (with their users) come in the same query.
    @EntityGraph(attributePaths = {"payer.user", "recipient.user"})
    List<Contribution> findByRoomIdOrderByCycleNumberAscIdAsc(Long roomId);

    // Only one cycle of a room.
    @EntityGraph(attributePaths = {"payer.user", "recipient.user"})
    List<Contribution> findByRoomIdAndCycleNumberOrderByIdAsc(Long roomId, int cycleNumber);

    // One contribution, making sure it belongs to the given room.
    @EntityGraph(attributePaths = {"payer.user", "recipient.user", "room"})
    Optional<Contribution> findByIdAndRoomId(Long id, Long roomId);

    // Used to check if the room is finished: are there any contributions not yet confirmed?
    boolean existsByRoomIdAndStatusNot(Long roomId, ContributionStatus status);
}
