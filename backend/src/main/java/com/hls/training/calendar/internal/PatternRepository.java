package com.hls.training.calendar.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PatternRepository extends JpaRepository<TrainingPattern, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TrainingPattern p where p.id = :id")
    Optional<TrainingPattern> findForUpdate(@Param("id") UUID id);

    List<TrainingPattern> findAllByOrderByStartMonthDescCreatedAtDesc();

    /** Patterns that have not been stopped: generation keeps these supplied. */
    List<TrainingPattern> findByEndMonthIsNull();
}
