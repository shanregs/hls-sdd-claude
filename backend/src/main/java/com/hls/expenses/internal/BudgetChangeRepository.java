package com.hls.expenses.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BudgetChangeRepository extends JpaRepository<BudgetChange, UUID> {

    List<BudgetChange> findByBudgetIdOrderByAtDescIdAsc(UUID budgetId);
}
