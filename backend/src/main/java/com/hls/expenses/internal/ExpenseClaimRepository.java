package com.hls.expenses.internal;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID>, JpaSpecificationExecutor<ExpenseClaim> {

    Optional<ExpenseClaim> findByClaimantIdAndClientRequestId(UUID claimantId, UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExpenseClaim c where c.id = :id")
    Optional<ExpenseClaim> lockById(@Param("id") UUID id);

    /** Other claims of the same claimant, date, amount and category that are not Rejected (FR-008). */
    @Query("select c from ExpenseClaim c where c.claimantId = :claimant and c.expenseDate = :date"
            + " and c.amount = :amount and c.category = :category and c.id <> :self"
            + " and c.status <> com.hls.expenses.internal.ClaimStatus.REJECTED")
    List<ExpenseClaim> duplicatesOf(
            @Param("claimant") UUID claimant,
            @Param("date") LocalDate date,
            @Param("amount") BigDecimal amount,
            @Param("category") ExpenseCategory category,
            @Param("self") UUID self);
}
