package com.hls.training.calendar.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    /** Takes the session row lock: seat checks and registrations serialise here (Constitution Principle VIII). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TrainingSession s where s.id = :id")
    Optional<TrainingSession> findForUpdate(@Param("id") UUID id);

    List<TrainingSession> findBySessionDateBetweenOrderBySessionDateAscStartsAtAsc(LocalDate from, LocalDate to);

    List<TrainingSession> findByPatternId(UUID patternId);

    Optional<TrainingSession> findByPatternIdAndPatternMonth(UUID patternId, LocalDate patternMonth);

    List<TrainingSession> findByStatusInAndSessionDateBetweenOrderBySessionDateAscStartsAtAsc(
            Collection<String> statuses, LocalDate from, LocalDate to);

    List<TrainingSession> findByIdIn(Collection<UUID> ids);

    /** Published and held sessions dated on or after the date, for the upcoming lists. */
    List<TrainingSession> findByStatusAndSessionDateGreaterThanEqualOrderBySessionDateAscStartsAtAsc(String status, LocalDate from);
}
