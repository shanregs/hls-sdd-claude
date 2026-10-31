package com.hls.payroll.run.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayrollFigures;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link PayrollFigures} over the stored runs and lines; reads only. */
@Service
class PayrollFiguresImpl implements PayrollFigures {

    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final PaymentService payments;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;

    PayrollFiguresImpl(
            PayRunRepository runs,
            PayRunLineRepository lines,
            PaymentService payments,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ManagerQueries managers) {
        this.runs = runs;
        this.lines = lines;
        this.payments = payments;
        this.teachers = teachers;
        this.schools = schools;
        this.managers = managers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunSummary> runOf(YearMonth month) {
        return pick(month).map(this::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonFigures> peopleOf(YearMonth month) {
        Optional<PayRun> run = pick(month);
        if (run.isEmpty()) {
            return List.of();
        }
        List<PayRunLine> all = lines.findByRunId(run.get().getId());
        Map<UUID, UUID> schoolOf = new HashMap<>();
        Map<UUID, UUID> zoneOf = new HashMap<>();
        placeTeachers(all, month, schoolOf, zoneOf);
        placeManagers(all, schoolOf, zoneOf);
        List<PersonFigures> out = new ArrayList<>();
        for (PayRunLine l : all) {
            boolean payable = l.getState() == PersonResult.State.PAYABLE;
            out.add(new PersonFigures(
                    l.getPersonId(),
                    l.getPersonKind(),
                    l.getPersonName(),
                    zoneOf.get(l.getPersonId()),
                    schoolOf.get(l.getPersonId()),
                    payable,
                    payable ? l.getEarned() : null,
                    payable ? l.getDeduction() : null,
                    payable ? l.getAdjustments() : null,
                    payable ? l.getNet() : null));
        }
        out.sort(Comparator.comparing(PersonFigures::name, String.CASE_INSENSITIVE_ORDER).thenComparing(PersonFigures::personId));
        return out;
    }

    private void placeTeachers(List<PayRunLine> all, YearMonth month, Map<UUID, UUID> schoolOf, Map<UUID, UUID> zoneOf) {
        Set<UUID> ids = all.stream()
                .filter(l -> l.getPersonKind() == Kind.TEACHER)
                .map(PayRunLine::getPersonId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, PlacementSpan> latest = new HashMap<>();
        for (PlacementSpan s : teachers.placementsOverlapping(ids, month.atDay(1), month.atEndOfMonth())) {
            latest.merge(s.teacherId(), s, (a, b) -> b.startsOn().isAfter(a.startsOn()) ? b : a);
        }
        Map<UUID, SchoolInfo> info = schools.schools(latest.values().stream().map(PlacementSpan::schoolId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SchoolInfo::id, s -> s));
        latest.forEach((teacher, span) -> {
            schoolOf.put(teacher, span.schoolId());
            SchoolInfo school = info.get(span.schoolId());
            if (school != null) {
                zoneOf.put(teacher, school.zoneId());
            }
        });
    }

    private void placeManagers(List<PayRunLine> all, Map<UUID, UUID> schoolOf, Map<UUID, UUID> zoneOf) {
        Set<UUID> ids = all.stream()
                .filter(l -> l.getPersonKind() == Kind.MANAGER)
                .map(PayRunLine::getPersonId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, Set<UUID>> current = managers.currentSchoolIds(ids);
        Set<UUID> allSchools = current.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<UUID, SchoolInfo> info = schools.schools(allSchools).stream().collect(Collectors.toMap(SchoolInfo::id, s -> s));
        current.forEach((manager, schoolIds) -> {
            Set<UUID> zones = new HashSet<>();
            schoolIds.forEach(id -> {
                SchoolInfo s = info.get(id);
                if (s != null) {
                    zones.add(s.zoneId());
                }
            });
            if (schoolIds.size() == 1) {
                schoolOf.put(manager, schoolIds.iterator().next());
            }
            if (zones.size() == 1) {
                zoneOf.put(manager, zones.iterator().next());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunSummary> runsBetween(YearMonth from, YearMonth to) {
        List<RunSummary> out = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            pick(m).map(this::summary).ifPresent(out::add);
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<YearMonth> lastApprovedMonth() {
        return runs.search(PayRun.Status.APPROVED, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst()
                .map(PayRun::getMonth);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnMonth> historyOf(Kind kind, UUID personId) {
        List<OwnMonth> out = new ArrayList<>();
        for (PayRunLine l : lines.findByPerson(kind, personId)) {
            Optional<PayRun> run = runs.findById(l.getRunId()).filter(r -> r.getStatus() == PayRun.Status.APPROVED);
            if (run.isEmpty()) {
                continue;
            }
            boolean payable = l.getState() == PersonResult.State.PAYABLE;
            out.add(new OwnMonth(
                    run.get().getMonth(),
                    stateOf(run.get()),
                    payable,
                    payable ? l.getEarned() : null,
                    payable ? l.getDeduction() : null,
                    payable ? l.getAdjustments() : null,
                    payable ? l.getNet() : null));
        }
        out.sort(Comparator.comparing(OwnMonth::month).reversed());
        return out;
    }

    /** The latest run of the month that is not cancelled, as the dashboard tile picks it. */
    private Optional<PayRun> pick(YearMonth month) {
        PayRun latest = null;
        for (PayRun r : runs.findByMonthOrderByKindAscSequenceAsc(month.atDay(1))) {
            if (r.getStatus() != PayRun.Status.CANCELLED) {
                latest = r;
            }
        }
        return Optional.ofNullable(latest);
    }

    private RunSummary summary(PayRun run) {
        return new RunSummary(
                run.getId(),
                run.getMonth(),
                stateOf(run),
                run.getTotalEarned(),
                run.getTotalDeduction(),
                run.getTotalAdjustments(),
                run.getTotalNet(),
                run.getPayableCount(),
                run.getCannotPayCount());
    }

    private RunState stateOf(PayRun run) {
        return switch (run.getStatus()) {
            case DRAFT, CANCELLED -> RunState.DRAFT;
            case AWAITING_APPROVAL -> RunState.AWAITING_APPROVAL;
            case APPROVED -> {
                PaymentService.Figures f = payments.figures(run);
                yield f.unpaidCount() == 0 && f.paidCount() > 0 ? RunState.PAID : RunState.APPROVED;
            }
        };
    }
}
