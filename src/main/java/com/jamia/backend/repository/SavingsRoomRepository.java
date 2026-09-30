package com.jamia.backend.repository;

import com.jamia.backend.entity.SavingsRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Reads and saves savings rooms.
 */
public interface SavingsRoomRepository extends JpaRepository<SavingsRoom, Long> {

    // Used when approving a join request and when starting the room. The lock makes other
    // requests for the same room wait, so two approvals can't take the last seat at the same moment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SavingsRoom r WHERE r.id = :id")
    Optional<SavingsRoom> findByIdForUpdate(@Param("id") Long id);
}
