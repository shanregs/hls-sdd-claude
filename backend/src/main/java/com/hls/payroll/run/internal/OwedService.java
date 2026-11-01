package com.hls.payroll.run.internal;

import com.hls.payroll.api.DayRateWork;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.school.api.NotFoundException;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
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
    private final PersonDirectory people;
    private final ObjectProvider<DayRateWork> dayRateWork;

    OwedService(
            JdbcTemplate jdbc,
            PayRunRepository runs,
            RunComputation computation,
            PersonDirectory people,
            ObjectProvider<DayRateWork> dayRateWork) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.computation = computation;
        this.people = people;
        this.dayRateWork = dayRateWork;
    }

    /** The people owed for a month, newest left-out reason first; no live check, so cheap enough to count. */
    @Transactional(readOnly = true)
    public List<OwedPerson> owed(YearMonth month) {
        List<OwedPerson> owed = new ArrayList<>(leftOut(month));
        owed.addAll(substitutesOwed(month, owed));
        return owed;
    }

    /**
     * Substitutes with Present days no run holds yet, for a month whose primary run is approved and in which they were
     * not paid (spec 017b FR-014). A Substitute already paid for the month is not here: that is a Correction.
     */
    private List<OwedPerson> substitutesOwed(YearMonth month, List<OwedPerson> already) {
        DayRateWork port = dayRateWork.getIfAvailable();
        Boolean primaryApproved = jdbc.queryForObject(
                "select exists (select 1 from payroll_run where month = ? and kind = 'PRIMARY' and status = 'APPROVED')",
                Boolean.class,
                Date.valueOf(month.atDay(1)));
        if (port == null || !Boolean.TRUE.equals(primaryApproved)) {
            return List.of();
        }
        Set<UUID> listed = new HashSet<>();
        already.forEach(o -> listed.add(o.personId()));
        Set<UUID> paid = new HashSet<>(jdbc.query(
                "select person_id from payroll_paid_month where month = ? and person_kind = 'TEACHER'",
                (rs, n) -> (UUID) rs.getObject("person_id"),
                Date.valueOf(month.atDay(1))));
        Set<UUID> ids = new HashSet<>(port.unpaidPresentDays(month).keySet());
        ids.removeAll(paid);
        ids.removeAll(listed);
        if (ids.isEmpty()) {
            return List.of();
        }
        java.util.Map<UUID, String> names = people.teacherNames(ids);
        return ids.stream()
                .map(id -> new OwedPerson(
                        Kind.TEACHER, id, names.getOrDefault(id, "Substitute"), "Present days not paid by the month's run.", false))
                .toList();
    }

    private List<OwedPerson> leftOut(YearMonth month) {
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
        List<OwedPerson> owedNow = owed(run.getMonth());
        Set<UUID> substitutes = people.substituteTeachers(owedNow.stream().map(OwedPerson::personId).toList());
        return owedNow.stream()
                .map(o -> new OwedPerson(
                        o.kind(),
                        o.personId(),
                        o.name(),
                        o.reason(),
                        // a Substitute has no placement, so the Teacher checks do not apply; the run prices the days
                        substitutes.contains(o.personId())
                                || computation.blockerNow(new PersonRef(o.kind(), o.personId()), run.getMonth()) == null))
                .sorted(java.util.Comparator.comparing(OwedPerson::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

}
