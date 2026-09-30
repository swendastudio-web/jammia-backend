package com.jamia.backend.repository;

import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads and saves contributions (payment tracking), per round.
 */
public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // All contributions of one round, by cycle. Payer and recipient (with their users) come in the same query.
    @EntityGraph(attributePaths = {"payer.user", "recipient.user", "round"})
    List<Contribution> findByRoundIdOrderByCycleNumberAscIdAsc(Long roundId);

    // One contribution, making sure it belongs to the given room.
    @EntityGraph(attributePaths = {"payer.user", "recipient.user", "room", "round"})
    Optional<Contribution> findByIdAndRoomId(Long id, Long roomId);

    // Reminders: a user's unpaid payments whose turn has started, in every room (also rooms they were removed from).
    @EntityGraph(attributePaths = {"room", "round", "payer", "recipient.user"})
    List<Contribution> findByPayerUserIdAndStatusAndDueAtLessThanEqualOrderByDueAtAsc(
            Long userId, ContributionStatus status, LocalDateTime now);

    // For the rounds history: how many payments, how many received.
    long countByRoundId(Long roundId);

    long countByRoundIdAndStatus(Long roundId, ContributionStatus status);
}
