package com.hls.expenses.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AreaBudgetRepository extends JpaRepository<AreaBudget, UUID> {

    Optional<AreaBudget> findByZoneIdAndMonth(UUID zoneId, LocalDate month);

    Optional<AreaBudget> findByZoneIdIsNullAndMonth(LocalDate month);

    List<AreaBudget> findByMonth(LocalDate month);
}
