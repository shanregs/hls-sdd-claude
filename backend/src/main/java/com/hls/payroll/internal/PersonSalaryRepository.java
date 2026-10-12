package com.hls.payroll.internal;

import com.hls.payroll.internal.PersonSalary.Kind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Save and query only: there is no update or delete, and the table refuses both (spec 013a FR-002). */
public interface PersonSalaryRepository extends JpaRepository<PersonSalary, UUID> {

    /** The row in effect on {@code date}: latest effective date, ties to the row recorded last. One index lookup. */
    Optional<PersonSalary>
            findFirstByPersonKindAndPersonIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(
                    Kind personKind, UUID personId, LocalDate date);

    List<PersonSalary> findByPersonKindAndPersonIdOrderByEffectiveOnDescRecordedAtDesc(Kind personKind, UUID personId);

    /** Every row of the given people on or before {@code date}; the one in effect is resolved in memory. */
    @Query("select s from PersonSalary s where s.personKind = :kind and s.personId in :ids and s.effectiveOn <= :date")
    List<PersonSalary> findCandidates(
            @Param("kind") Kind kind, @Param("ids") Collection<UUID> ids, @Param("date") LocalDate date);
}
