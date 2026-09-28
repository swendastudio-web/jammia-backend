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

    // Used to make sure a new join code is not already taken.
    boolean existsByJoinCode(String joinCode);

    // Used by join and start. The lock makes other requests for the same room wait,
    // so two people can't take the last seat at the same moment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SavingsRoom r WHERE r.joinCode = :joinCode")
    Optional<SavingsRoom> findByJoinCodeForUpdate(@Param("joinCode") String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SavingsRoom r WHERE r.id = :id")
    Optional<SavingsRoom> findByIdForUpdate(@Param("id") Long id);
}
