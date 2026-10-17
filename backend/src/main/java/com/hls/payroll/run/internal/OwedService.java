package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.school.api.NotFoundException;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is still owed pay for a month (spec 013b US5, research 9 and 10): the people an approved run left out as "cannot
 * be paid", less everyone who now has a row in {@code payroll_paid_month}. The reason shown is the one stored with the
 * line they were left out with. A live "resolved" hint says whether the same checks would let them be paid now; it is
 * only a hint for the Admin and never an input to any figure.
 */
@Service
public class OwedService {

    public record OwedPerson(Kind kind, UUID personId, String name, String reason, boolean resolved) {}

    private final JdbcTemplate jdbc;
    private final PayRunRepository runs;
    private final RunComputation computation;

    OwedService(JdbcTemplate jdbc, PayRunRepository runs, RunComputation computation) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.computation = computation;
    }

    /** The people owed for a month, newest left-out reason first; no live check, so cheap enough to count. */
    @Transactional(readOnly = true)
    public List<OwedPerson> owed(YearMonth month) {
        return jdbc.query(
                "select distinct on (l.person_kind, l.person_id) l.person_kind, l.person_id, l.person_name, l.reason"
                        + " from payroll_run_line l join payroll_run r on r.id = l.run_id"
                        + " where r.month = ? and r.status = 'APPROVED' and l.state = 'CANNOT_PAY'"
                        + " and not exists (select 1 from payroll_paid_month p where p.month = r.month"
                        + " and p.person_kind = l.person_kind and p.person_id = l.person_id)"
                        + " order by l.person_kind, l.person_id, r.sequence desc, r.kind desc",
                (rs, n) -> new OwedPerson(
                        Kind.valueOf(rs.getString("person_kind")),
                        (UUID) rs.getObject("person_id"),
                        rs.getString("person_name"),
                        rs.getString("reason"),
                        false),
                Date.valueOf(month.atDay(1)));
    }

    /** The people owed for the month of a run, each with the live hint. */
    @Transactional(readOnly = true)
    public List<OwedPerson> owedFor(UUID runId) {
        PayRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        return owed(run.getMonth()).stream()
                .map(o -> new OwedPerson(
                        o.kind(),
                        o.personId(),
                        o.name(),
                        o.reason(),
                        computation.blockerNow(new PersonRef(o.kind(), o.personId()), run.getMonth()) == null))
                .sorted(java.util.Comparator.comparing(OwedPerson::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

}
