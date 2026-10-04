package com.hls.attendance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonWorkingDateRepository extends JpaRepository<NonWorkingDate, UUID> {

    Optional<NonWorkingDate> findByOnDate(LocalDate onDate);

    List<NonWorkingDate> findAllByOrderByOnDate();

    List<NonWorkingDate> findByOnDateBetween(LocalDate from, LocalDate to);
}
