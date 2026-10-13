package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PayRunLineRepository extends JpaRepository<PayRunLine, UUID> {

    List<PayRunLine> findByRunId(UUID runId);

    List<PayRunLine> findByRunIdAndState(UUID runId, PersonResult.State state);

    /** Always called with non-null filters (all states, all kinds, "%") so the statement is one fixed shape. */
    @Query("select l from PayRunLine l where l.runId = :runId and l.state in :states and l.personKind in :kinds"
            + " and lower(l.personName) like :query order by l.personName, l.id")
    Page<PayRunLine> search(
            @Param("runId") UUID runId,
            @Param("states") Collection<PersonResult.State> states,
            @Param("kinds") Collection<Kind> kinds,
            @Param("query") String query,
            Pageable pageable);

    @Query("select l from PayRunLine l where l.personKind = :kind and l.personId = :personId order by l.computedAt desc")
    List<PayRunLine> findByPerson(@Param("kind") Kind kind, @Param("personId") UUID personId);
}
