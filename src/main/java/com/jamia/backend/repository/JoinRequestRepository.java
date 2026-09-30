package com.jamia.backend.repository;

import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves requests to join rooms.
 */
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    // The room creator's list, oldest first. Requester and referring member come in the same query.
    @EntityGraph(attributePaths = {"user", "referredBy.user"})
    List<JoinRequest> findByRoomIdAndStatusOrderByCreatedAtAsc(Long roomId, JoinRequestStatus status);

    // Has this person already asked (and is still waiting)?
    boolean existsByRoomIdAndUserIdAndStatus(Long roomId, Long userId, JoinRequestStatus status);

    // One request, making sure it belongs to the given room.
    @EntityGraph(attributePaths = {"user", "referredBy.user", "room"})
    Optional<JoinRequest> findByIdAndRoomId(Long id, Long roomId);
}
