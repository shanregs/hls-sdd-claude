package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.performance.api.PerformanceSummary;
import com.hls.performance.internal.PerformanceTypes.FeedbackCategory;
import com.hls.performance.internal.PerformanceTypes.FeedbackStatus;
import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import com.hls.performance.internal.PerformanceTypes.NotHeldReason;
import com.hls.performance.internal.PerformanceTypes.ObservationStatus;
import com.hls.performance.internal.PerformanceTypes.PlanStatus;
import com.hls.performance.internal.PerformanceTypes.Result;
import com.hls.performance.internal.TeacherFacts.TeacherContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads for the Teacher performance screens (spec 026 US6): the lists with their filters, the Teacher page and the summary. */
@Service
public class PerformanceQueries implements PerformanceSummary {

    public static final int MAX_PAGE_SIZE = 100;

    /** The filters shared by the lists; every field is optional. */
    public record Filter(
            UUID zoneId, UUID schoolId, UUID managerId, UUID teacherId, String result, String status, LocalDate from, LocalDate to, Boolean principalDeclined) {}

    public record ObservationRow(
            UUID id,
            UUID rootId,
            int versionNo,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            String observerName,
            LocalDate observedOn,
            String status,
            String overallResult,
            BigDecimal averageRating,
            String notHeldReason,
            boolean principalDeclined,
            boolean permissionRecorded,
            UUID issueId) {}

    public record FeedbackRow(
            UUID id,
            UUID rootId,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            String principalName,
            LocalDate givenOn,
            String category,
            String categoryLabel,
            boolean actionRequired,
            String status,
            UUID issueId) {}

    public record IssueRow(
            UUID id,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            String category,
            String status,
            String resolution,
            int correctionRounds,
            LocalDate openedOn,
            LocalDate closedOn,
            String managerName) {}

    /** One line of the Teacher page, in date order. */
    public record Entry(String type, UUID id, LocalDate date, String title, String result, String status) {}

    public record TeacherPage(
            UUID teacherId,
            String teacherName,
            String status,
            UUID schoolId,
            String schoolName,
            String managerName,
            String latestResult,
            PlanService.PlanView openPlan,
            List<Entry> entries,
            List<ObservationRow> observations,
            List<FeedbackRow> feedback,
            List<IssueRow> issues) {}

    private final ObservationRepository observations;
    private final FeedbackRepository feedback;
    private final IssueRepository issues;
    private final PlanRepository plans;
    private final ReferralRepository referrals;
    private final PlanService planService;
    private final TeacherFacts facts;
    private final ScopeGuard scope;
    private final SchoolDirectory schools;
    private final BusinessDate businessDate;

    public PerformanceQueries(
            ObservationRepository observations,
            FeedbackRepository feedback,
            IssueRepository issues,
            PlanRepository plans,
            ReferralRepository referrals,
            PlanService planService,
            TeacherFacts facts,
            ScopeGuard scope,
            SchoolDirectory schools,
            BusinessDate businessDate) {
        this.observations = observations;
        this.feedback = feedback;
        this.issues = issues;
        this.plans = plans;
        this.referrals = referrals;
        this.planService = planService;
        this.facts = facts;
        this.scope = scope;
        this.schools = schools;
        this.businessDate = businessDate;
    }

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public PageResponse<ObservationRow> observations(Caller caller, Filter f, int page, int size) {
        Specification<Observation> spec = (root, q, cb) -> cb.isTrue(root.get("current"));
        spec = spec.and(scoped(caller, "observerUserId")).and(inSchools(f.zoneId(), f.schoolId(), "schoolId"));
        spec = spec.and(equal("teacherId", f.teacherId())).and(equal("managerUserId", f.managerId()));
        if (f.result() != null && !f.result().isBlank()) {
            Result result = parse(Result.class, f.result(), "result");
            spec = spec.and(equal("overallResult", result));
        }
        if (f.status() != null && !f.status().isBlank()) {
            spec = spec.and(equal("status", parse(ObservationStatus.class, f.status(), "status")));
        }
        spec = spec.and(between("observedOn", f.from(), f.to()));
        if (Boolean.TRUE.equals(f.principalDeclined())) {
            spec = spec.and(equal("notHeldReason", NotHeldReason.PRINCIPAL_DECLINED));
        }
        var result = observations.findAll(spec, pageable(page, size, Sort.by(Sort.Order.desc("observedOn"), Sort.Order.desc("createdAt"))));
        Names names = names(result.getContent().stream().map(o -> o.teacherId).toList(), result.getContent().stream().map(o -> o.schoolId).toList());
        List<ObservationRow> rows = result.getContent().stream().map(o -> observationRow(o, names)).toList();
        return new PageResponse<>(rows, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackRow> feedback(Caller caller, Filter f, Boolean actionRequired, int page, int size) {
        Specification<PrincipalFeedback> spec = (root, q, cb) -> cb.isTrue(root.get("current"));
        spec = spec.and(scoped(caller, "recordedBy")).and(inSchools(f.zoneId(), f.schoolId(), "schoolId"));
        spec = spec.and(equal("teacherId", f.teacherId())).and(between("givenOn", f.from(), f.to()));
        if (f.result() != null && !f.result().isBlank()) {
            spec = spec.and(equal("category", parse(FeedbackCategory.class, f.result(), "category")));
        }
        if (f.status() != null && !f.status().isBlank()) {
            spec = spec.and(equal("status", parse(FeedbackStatus.class, f.status(), "status")));
        } else {
            spec = spec.and((root, q, cb) -> cb.notEqual(root.get("status"), FeedbackStatus.SUPERSEDED));
        }
        if (actionRequired != null) {
            spec = spec.and(equal("actionRequired", actionRequired));
        }
        var result = feedback.findAll(spec, pageable(page, size, Sort.by(Sort.Order.desc("givenOn"), Sort.Order.desc("createdAt"))));
        Names names = names(result.getContent().stream().map(x -> x.teacherId).toList(), result.getContent().stream().map(x -> x.schoolId).toList());
        List<FeedbackRow> rows = result.getContent().stream()
                .map(x -> new FeedbackRow(x.id, x.rootId, x.teacherId, names.teachers.get(x.teacherId), x.schoolId, names.schools.get(x.schoolId),
                        x.principalName, x.givenOn, x.category.name(), x.category.label(), x.actionRequired, x.status.name(), x.issueId))
                .toList();
        return new PageResponse<>(rows, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** {@code view} is "open" (Open and Under correction), "referred", or empty to use {@code f.status()}. */
    @Transactional(readOnly = true)
    public PageResponse<IssueRow> issues(Caller caller, String view, Filter f, int page, int size) {
        Specification<PerformanceIssue> spec = scoped(caller, "openedBy");
        spec = spec.and(inSchools(f.zoneId(), f.schoolId(), "schoolId")).and(equal("teacherId", f.teacherId())).and(equal("managerUserId", f.managerId()));
        spec = spec.and(between("openedOn", f.from(), f.to()));
        if ("open".equalsIgnoreCase(view)) {
            spec = spec.and((root, q, cb) -> root.get("status").in(IssueStatus.OPEN, IssueStatus.UNDER_CORRECTION));
        } else if ("referred".equalsIgnoreCase(view)) {
            spec = spec.and(equal("status", IssueStatus.REFERRED));
        } else if (f.status() != null && !f.status().isBlank()) {
            spec = spec.and(equal("status", parse(IssueStatus.class, f.status(), "status")));
        }
        if (f.result() != null && !f.result().isBlank()) {
            spec = spec.and(equal("category", f.result().trim().toUpperCase()));
        }
        var result = issues.findAll(spec, pageable(page, size, Sort.by(Sort.Order.desc("openedOn"), Sort.Order.desc("createdAt"))));
        Names names = names(result.getContent().stream().map(i -> i.teacherId).toList(), result.getContent().stream().map(i -> i.schoolId).toList());
        List<IssueRow> rows = result.getContent().stream().map(i -> issueRow(i, names)).toList();
        return new PageResponse<>(rows, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * The Planned or Overdue list: each Teacher's open Observation task with its date, reason, days overdue and last result.
     * Admin and Director opening it first bring the tasks up to date with the Teachers' current Managers.
     */
    @Transactional
    public List<PlanService.PlanView> planned(Caller caller, String view, Filter f) {
        if (caller.orgWide()) {
            planService.reconcile();
        }
        LocalDate today = businessDate.today();
        Set<UUID> inZoneOrSchool = schoolIds(f.zoneId(), f.schoolId());
        TeacherScope teacherScope = scope.scope(caller);
        List<PlanService.PlanView> views = new ArrayList<>();
        for (ObservationPlan plan : plans.findByStatus(PlanStatus.OPEN)) {
            if (!(teacherScope.allows(plan.teacherId) || plan.plannedBy.equals(caller.userId()))) {
                continue;
            }
            if (inZoneOrSchool != null && !inZoneOrSchool.contains(plan.schoolId)) {
                continue;
            }
            if (f.teacherId() != null && !f.teacherId().equals(plan.teacherId)) {
                continue;
            }
            if ("overdue".equalsIgnoreCase(view) && !plan.plannedOn.isBefore(today)) {
                continue;
            }
            TeacherContext teacher = facts.find(plan.teacherId).orElse(null);
            if (f.managerId() != null && (teacher == null || !f.managerId().equals(teacher.managerUserId()))) {
                continue;
            }
            views.add(planService.view(plan, teacher, false, caller));
        }
        views.sort(Comparator.comparing(PlanService.PlanView::plannedOn).thenComparing(v -> v.teacherName() == null ? "" : v.teacherName()));
        return views;
    }

    // ------------------------------------------------------------------ the Teacher page

    @Transactional(readOnly = true)
    public TeacherPage teacher(Caller caller, UUID teacherId) {
        // a Manager who wrote a record about a Teacher who has since moved still sees that record, but not the rest
        boolean inScope = scope.scope(caller).allows(teacherId);
        TeacherContext teacher = facts.require(teacherId);
        List<Observation> obs = observations.findByTeacherIdAndCurrentTrueOrderByObservedOnDescCreatedAtDesc(teacherId).stream()
                .filter(o -> inScope || o.observerUserId.equals(caller.userId()))
                .toList();
        List<PrincipalFeedback> notes = feedback.findByTeacherIdAndCurrentTrueOrderByGivenOnDescCreatedAtDesc(teacherId).stream()
                .filter(f -> inScope || f.recordedBy.equals(caller.userId()))
                .toList();
        List<PerformanceIssue> teacherIssues = issues.findByTeacherIdOrderByOpenedOnDescCreatedAtDesc(teacherId).stream()
                .filter(i -> inScope || (i.openedBy != null && i.openedBy.equals(caller.userId())))
                .toList();
        if (!inScope && obs.isEmpty() && notes.isEmpty() && teacherIssues.isEmpty()) {
            throw new com.hls.school.api.NotFoundException("Teacher not found.");
        }
        Names names = names(List.of(teacherId), java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(obs.stream().map(o -> o.schoolId), notes.stream().map(f -> f.schoolId)),
                teacherIssues.stream().map(i -> i.schoolId)).toList());
        List<ObservationRow> obsRows = obs.stream().map(o -> observationRow(o, names)).toList();
        List<FeedbackRow> noteRows = notes.stream()
                .filter(f -> f.status != FeedbackStatus.SUPERSEDED)
                .map(f -> new FeedbackRow(f.id, f.rootId, f.teacherId, names.teachers.get(f.teacherId), f.schoolId, names.schools.get(f.schoolId),
                        f.principalName, f.givenOn, f.category.name(), f.category.label(), f.actionRequired, f.status.name(), f.issueId))
                .toList();
        List<IssueRow> issueRows = teacherIssues.stream().map(i -> issueRow(i, names)).toList();
        List<Entry> entries = new ArrayList<>();
        for (ObservationRow o : obsRows) {
            entries.add(new Entry("OBSERVATION", o.id(), o.observedOn(), o.status().equals("NOT_HELD") ? "Visit not held" : "Observation", o.overallResult(), o.status()));
        }
        for (FeedbackRow f : noteRows) {
            entries.add(new Entry("FEEDBACK", f.id(), f.givenOn(), "Principal feedback: " + f.categoryLabel(), null, f.status()));
        }
        for (IssueRow i : issueRows) {
            entries.add(new Entry("ISSUE", i.id(), i.openedOn(), "Performance issue: " + i.category(), null, i.status()));
        }
        referrals.findByIssueIdIn(teacherIssues.stream().map(i -> i.id).toList())
                .forEach(r -> entries.add(new Entry("REFERRAL", r.id, r.referredOn, "Referral: " + r.recommendation.name().toLowerCase(), null,
                        r.decisionOutcome == null ? "PENDING" : r.decisionOutcome.name())));
        entries.sort(Comparator.comparing(Entry::date).reversed());
        String latest = obs.stream()
                .filter(o -> o.status == ObservationStatus.SUBMITTED && o.overallResult != null)
                .findFirst()
                .map(o -> o.overallResult.name())
                .orElse(null);
        PlanService.PlanView openPlan = inScope
                ? plans.findFirstByTeacherIdAndStatus(teacherId, PlanStatus.OPEN).map(p -> planService.view(p, teacher, false, caller)).orElse(null)
                : null;
        return new TeacherPage(teacherId, teacher.name(), teacher.status(), teacher.schoolId(), teacher.schoolName(), teacher.managerName(), latest, openPlan,
                entries, obsRows, noteRows, issueRows);
    }

    // ------------------------------------------------------------------ summary for the dashboard (028)

    /** The caller's view of the summary: Admin and Director every Manager, a Manager only themselves. */
    @Transactional(readOnly = true)
    public Map<UUID, Counts> byManager(Caller caller, LocalDate from, LocalDate to) {
        Map<UUID, Counts> all = byManager(from, to);
        if (caller.orgWide()) {
            return all;
        }
        Map<UUID, Counts> own = new HashMap<>();
        if (all.containsKey(caller.userId())) {
            own.put(caller.userId(), all.get(caller.userId()));
        }
        return own;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Counts> byZone(Caller caller, LocalDate from, LocalDate to) {
        if (!caller.orgWide()) {
            return Map.of();
        }
        return byZone(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Counts> byManager(LocalDate from, LocalDate to) {
        return tally(from, to, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Counts> byZone(LocalDate from, LocalDate to) {
        return tally(from, to, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Counts> byTeacher(LocalDate from, LocalDate to) {
        return tally(from, to, Dimension.TEACHER);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Counts> bySchool(LocalDate from, LocalDate to) {
        return tally(from, to, Dimension.SCHOOL);
    }

    private enum Dimension {
        MANAGER,
        ZONE,
        TEACHER,
        SCHOOL
    }

    private Map<UUID, Counts> tally(LocalDate from, LocalDate to, boolean byManager) {
        return tally(from, to, byManager ? Dimension.MANAGER : Dimension.ZONE);
    }

    private Map<UUID, Counts> tally(LocalDate from, LocalDate to, Dimension dim) {
        boolean byManager = dim == Dimension.MANAGER;
        LocalDate today = businessDate.today();
        Map<UUID, long[]> table = new HashMap<>();
        List<Observation> done = observations.findAll(
                (root, q, cb) -> cb.and(cb.isTrue(root.get("current")), cb.equal(root.get("status"), ObservationStatus.SUBMITTED),
                        cb.between(root.get("observedOn"), from, to)));
        Set<UUID> schoolIds = new HashSet<>();
        done.forEach(o -> schoolIds.add(o.schoolId));
        List<ObservationPlan> open = plans.findByStatus(PlanStatus.OPEN);
        open.forEach(p -> schoolIds.add(p.schoolId));
        List<PerformanceIssue> allIssues = issues.findAll();
        allIssues.forEach(i -> schoolIds.add(i.schoolId));
        Map<UUID, UUID> zoneOf = dim == Dimension.ZONE ? facts.schoolZones(schoolIds) : Map.of();
        for (Observation o : done) {
            bump(table, key(dim, o.teacherId, o.managerUserId, zoneOf.get(o.schoolId), o.schoolId), 0);
        }
        for (ObservationPlan p : open) {
            UUID manager = byManager ? facts.find(p.teacherId).map(TeacherContext::managerUserId).orElse(null) : null;
            UUID key = key(dim, p.teacherId, manager, zoneOf.get(p.schoolId), p.schoolId);
            bump(table, key, 1);
            if (p.plannedOn.isBefore(today)) {
                bump(table, key, 2);
            }
        }
        for (PerformanceIssue i : allIssues) {
            UUID key = key(dim, i.teacherId, i.managerUserId, zoneOf.get(i.schoolId), i.schoolId);
            if (i.status == IssueStatus.OPEN || i.status == IssueStatus.UNDER_CORRECTION) {
                bump(table, key, 3);
            }
            if (i.status == IssueStatus.RESOLVED && i.closedOn != null && !i.closedOn.isBefore(from) && !i.closedOn.isAfter(to)) {
                bump(table, key, 5);
            }
        }
        Map<UUID, PerformanceIssue> issueById = new HashMap<>();
        allIssues.forEach(i -> issueById.put(i.id, i));
        for (Referral r : referrals.findAll()) {
            PerformanceIssue i = issueById.get(r.issueId);
            if (i != null && !r.referredOn.isBefore(from) && !r.referredOn.isAfter(to)) {
                bump(table, key(dim, i.teacherId, i.managerUserId, zoneOf.get(i.schoolId), i.schoolId), 4);
            }
        }
        Map<UUID, Counts> result = new HashMap<>();
        table.forEach((k, v) -> {
            if (k != null) {
                result.put(k, new Counts(v[0], v[1], v[2], v[3], v[4], v[5]));
            }
        });
        return result;
    }

    private static UUID key(Dimension dim, UUID teacher, UUID manager, UUID zone, UUID school) {
        return switch (dim) {
            case MANAGER -> manager;
            case ZONE -> zone;
            case TEACHER -> teacher;
            case SCHOOL -> school;
        };
    }

    private static void bump(Map<UUID, long[]> table, UUID key, int slot) {
        if (key == null) {
            return;
        }
        table.computeIfAbsent(key, k -> new long[6])[slot]++;
    }

    // ------------------------------------------------------------------ helpers

    private record Names(Map<UUID, String> teachers, Map<UUID, String> schools) {}

    private Names names(Collection<UUID> teacherIds, Collection<UUID> schoolIds) {
        return new Names(facts.teacherNames(new HashSet<>(teacherIds)), facts.schoolNames(new HashSet<>(schoolIds)));
    }

    private ObservationRow observationRow(Observation o, Names names) {
        return new ObservationRow(o.id, o.rootId, o.versionNo, o.teacherId, names.teachers.get(o.teacherId), o.schoolId, names.schools.get(o.schoolId),
                o.observerName, o.observedOn, o.status.name(), o.overallResult == null ? null : o.overallResult.name(), o.averageRating,
                o.notHeldReason == null ? null : o.notHeldReason.name(), o.notHeldReason == NotHeldReason.PRINCIPAL_DECLINED, o.permissionBy != null,
                o.issueId);
    }

    private IssueRow issueRow(PerformanceIssue i, Names names) {
        return new IssueRow(i.id, i.teacherId, names.teachers.get(i.teacherId), i.schoolId, names.schools.get(i.schoolId), i.category, i.status.name(),
                i.resolution == null ? null : i.resolution.name(), i.correctionRounds, i.openedOn, i.closedOn, facts.userName(i.managerUserId));
    }

    /** The scope rule as a query predicate: org-wide, or a Teacher in scope today, or a record the caller wrote. */
    private <T> Specification<T> scoped(Caller caller, String authorField) {
        if (caller.orgWide()) {
            return (root, q, cb) -> cb.conjunction();
        }
        TeacherScope teacherScope = scope.scope(caller);
        return (root, q, cb) -> {
            Predicate own = cb.equal(root.get(authorField), caller.userId());
            if (teacherScope.teacherIds().isEmpty()) {
                return own;
            }
            return cb.or(own, root.get("teacherId").in(teacherScope.teacherIds()));
        };
    }

    private <T> Specification<T> inSchools(UUID zoneId, UUID schoolId, String field) {
        Set<UUID> ids = schoolIds(zoneId, schoolId);
        if (ids == null) {
            return (root, q, cb) -> cb.conjunction();
        }
        if (ids.isEmpty()) {
            return (root, q, cb) -> cb.disjunction();
        }
        return (root, q, cb) -> root.get(field).in(ids);
    }

    /** The Schools a Zone or School filter allows; null when no such filter was given. */
    private Set<UUID> schoolIds(UUID zoneId, UUID schoolId) {
        if (zoneId == null && schoolId == null) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        if (zoneId != null) {
            schools.schoolsInZone(zoneId).forEach(s -> ids.add(s.id()));
            if (schoolId != null) {
                ids.retainAll(Set.of(schoolId));
            }
        } else {
            ids.add(schoolId);
        }
        return ids;
    }

    private static <T> Specification<T> equal(String field, Object value) {
        return value == null ? (root, q, cb) -> cb.conjunction() : (root, q, cb) -> cb.equal(root.get(field), value);
    }

    private static <T> Specification<T> between(String field, LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            if (from != null) {
                parts.add(cb.greaterThanOrEqualTo(root.get(field), from));
            }
            if (to != null) {
                parts.add(cb.lessThanOrEqualTo(root.get(field), to));
            }
            return cb.and(parts.toArray(Predicate[]::new));
        };
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String what) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown " + what + ": " + raw + ".");
        }
    }
}
