package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.performance.internal.PerformanceTypes.FeedbackCategory;
import com.hls.performance.internal.PerformanceTypes.Result;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherScopeQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): for the first seeded Manager's Teachers, a
 * Good observation, a Needs improvement observation with a corrective action and its follow-up task, and Principal
 * feedback with a planned observation, so every list and the Teacher page have something to show.
 */
@Component
@Order(47)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class PerformanceDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PerformanceDevSeeder.class);

    private final ObservationService observations;
    private final FeedbackService feedback;
    private final IssueService issues;
    private final PlanService plans;
    private final ObservationRepository observationRepository;
    private final RoleAssignmentRepository roles;
    private final ManagerQueries managers;
    private final TeacherScopeQueries scopes;
    private final TeacherDirectory teachers;
    private final BusinessDate businessDate;

    PerformanceDevSeeder(
            ObservationService observations,
            FeedbackService feedback,
            IssueService issues,
            PlanService plans,
            ObservationRepository observationRepository,
            RoleAssignmentRepository roles,
            ManagerQueries managers,
            TeacherScopeQueries scopes,
            TeacherDirectory teachers,
            BusinessDate businessDate) {
        this.observations = observations;
        this.feedback = feedback;
        this.issues = issues;
        this.plans = plans;
        this.observationRepository = observationRepository;
        this.roles = roles;
        this.managers = managers;
        this.scopes = scopes;
        this.teachers = teachers;
        this.businessDate = businessDate;
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID manager = roles.userIdsWithRole(Role.MANAGER).stream()
                .filter(id -> managers.managerByUserId(id).filter(ManagerQueries.ManagerRef::active).isPresent())
                .findFirst()
                .orElse(null);
        if (manager == null) {
            return;
        }
        Caller caller = new Caller(manager, Set.of(Role.MANAGER));
        Set<UUID> placed = scopes.teacherScope(manager, caller.roles()).teacherIds();
        List<UUID> active = teachers.teacherInfo(placed).values().stream()
                .filter(t -> "ACTIVE".equals(t.status()))
                .sorted(java.util.Comparator.comparing(TeacherDirectory.TeacherInfo::name))
                .map(TeacherDirectory.TeacherInfo::id)
                .toList();
        if (active.size() < 3 || !observationRepository.findByTeacherIdAndCurrentTrueOrderByObservedOnDescCreatedAtDesc(active.get(0)).isEmpty()) {
            return;
        }
        LocalDate today = businessDate.today();
        observe(caller, active.get(0), Result.GOOD, 5, today.minusDays(20));

        UUID struggling = active.get(1);
        observe(caller, struggling, Result.NEEDS_IMPROVEMENT, 3, today.minusDays(5));
        PerformanceIssue issue = issues.activeOf(struggling).orElseThrow();
        issues.recordCorrectiveAction(caller, issue.id,
                new IssueService.CorrectiveRequest("Talked through the lesson plan and the homework checking", "Check homework daily and plan lessons in steps",
                        today.plusDays(9), issue.version));

        UUID reported = active.get(2);
        var note = feedback.record(caller, new FeedbackService.RecordRequest(reported, "Demo Principal", today.minusDays(1), FeedbackCategory.ATTENDANCE,
                "Arrives late on most Mondays and the first class starts without her", true, null));
        plans.planFromFeedback(caller, reported, note.rootId(), today.plusDays(2));
        log.info("[DEV SEED] Teacher performance demo data ready.");
    }

    private void observe(Caller caller, UUID teacher, Result result, int rating, LocalDate on) {
        var draft = observations.start(caller, teacher, on);
        Map<String, Integer> ratings = new java.util.LinkedHashMap<>();
        for (PerformanceTypes.Area area : PerformanceTypes.Area.values()) {
            ratings.put(area.name(), rating);
        }
        observations.submit(caller, draft.id(), new ObservationService.SaveRequest(on, "Class 5", "English", 40, ratings, result, "Demo observation",
                "Clear voice and good rapport with the pupils", result == Result.GOOD ? null : "Plan the lesson in steps and check understanding", null, null,
                draft.version()));
    }
}
