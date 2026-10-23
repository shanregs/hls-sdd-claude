package com.hls.performance.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.performance.api.PerformanceSummary.Counts;
import com.hls.performance.internal.Caller;
import com.hls.performance.internal.PerformanceQueries;
import com.hls.performance.internal.PerformanceQueries.TeacherPage;
import com.hls.performance.internal.PerformanceTypes.Area;
import com.hls.performance.internal.PerformanceTypes.FeedbackCategory;
import com.hls.performance.internal.PerformanceTypes.NotHeldReason;
import com.hls.performance.internal.PerformanceTypes.PermissionWay;
import com.hls.performance.internal.PerformanceTypes.PlanReason;
import com.hls.performance.internal.PerformanceTypes.Result;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Teacher's performance page, the dashboard summary and the coded values the screens need. */
@RestController
@RequestMapping("/api/v1/teacher-performance")
public class TeacherPerformanceController {

    private final PerformanceQueries queries;
    private final PermissionGuard guard;

    public TeacherPerformanceController(PerformanceQueries queries, PermissionGuard guard) {
        this.queries = queries;
        this.guard = guard;
    }

    public record Option(String value, String label) {}

    public record Options(
            List<Option> areas,
            List<Option> results,
            List<Option> notHeldReasons,
            List<Option> permissionWays,
            List<Option> feedbackCategories,
            List<Option> planReasons) {}

    public record Summary(LocalDate from, LocalDate to, Map<UUID, Counts> byManager, Map<UUID, Counts> byZone) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_PERFORMANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping("/teachers/{teacherId}")
    public TeacherPage teacher(@PathVariable UUID teacherId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.teacher(caller(jwt), teacherId);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam LocalDate from, @RequestParam LocalDate to, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        if (to.isBefore(from)) {
            throw new InvalidInputException("The end of the period cannot be before its start.");
        }
        Caller caller = caller(jwt);
        return new Summary(from, to, queries.byManager(caller, from, to), queries.byZone(caller, from, to));
    }

    @GetMapping("/options")
    public Options options(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return new Options(
                Arrays.stream(Area.values()).map(a -> new Option(a.name(), a.label())).toList(),
                Arrays.stream(Result.values()).map(a -> new Option(a.name(), a.label())).toList(),
                Arrays.stream(NotHeldReason.values()).map(a -> new Option(a.name(), a.label())).toList(),
                Arrays.stream(PermissionWay.values()).map(a -> new Option(a.name(), a.name().charAt(0) + a.name().substring(1).toLowerCase().replace('_', ' '))).toList(),
                Arrays.stream(FeedbackCategory.values()).map(a -> new Option(a.name(), a.label())).toList(),
                Arrays.stream(PlanReason.values()).map(a -> new Option(a.name(), a.label())).toList());
    }
}
