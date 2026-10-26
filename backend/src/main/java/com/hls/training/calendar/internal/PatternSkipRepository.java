package com.hls.training.calendar.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatternSkipRepository extends JpaRepository<PatternSkip, UUID> {

    List<PatternSkip> findByPatternId(UUID patternId);

    List<PatternSkip> findByPatternIdIn(Collection<UUID> patternIds);

    Optional<PatternSkip> findByPatternIdAndMonth(UUID patternId, LocalDate month);
}
