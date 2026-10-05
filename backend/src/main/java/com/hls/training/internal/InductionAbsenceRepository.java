package com.hls.training.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InductionAbsenceRepository extends JpaRepository<InductionAbsence, UUID> {

    Optional<InductionAbsence> findByEnrolmentIdAndAbsentOn(UUID enrolmentId, LocalDate absentOn);

    List<InductionAbsence> findByEnrolmentIdIn(Collection<UUID> enrolmentIds);
}
