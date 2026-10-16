package com.hls.attendance.workingdays.web;

import com.hls.attendance.workingdays.internal.WorkingDaysGridService;
import com.hls.attendance.workingdays.internal.WorkingDaysGridService.Page;
import com.hls.attendance.workingdays.internal.WorkingDaysGridService.SchoolOption;
import com.hls.attendance.workingdays.internal.WorkingDaysHistoryService;
import com.hls.attendance.workingdays.internal.WorkingDaysHistoryService.DeletedEntry;
import com.hls.attendance.workingdays.internal.WorkingDaysHistoryService.Entry;
import com.hls.attendance.workingdays.internal.WorkingDaysService;
import com.hls.attendance.workingdays.internal.WorkingDaysViewFactory;
import com.hls.attendance.workingdays.internal.WorkingDaysViewFactory.RowView;
import com.hls.attendance.workingdays.internal.YearCopyService;
import com.hls.attendance.workingdays.internal.YearEntryService;
import com.hls.attendance.workingdays.internal.YearEntryService.SaveRequest;
import com.hls.attendance.workingdays.internal.YearEntryService.SaveResult;
import com.hls.attendance.workingdays.internal.YearEntryService.YearView;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The School Working Days screen's API (contracts/school-working-days-api.md), gated by SCHOOL_WORKING_DAYS grants. */
@RestController
@RequestMapping("/api/v1/school-working-days")
public class SchoolWorkingDaysController {

    private static final PermissionModule MODULE = PermissionModule.SCHOOL_WORKING_DAYS;

    private final WorkingDaysService service;
    private final WorkingDaysGridService grid;
    private final WorkingDaysViewFactory views;
    private final WorkingDaysHistoryService history;
    private final YearCopyService copy;
    private final YearEntryService yearEntry;
    private final PermissionGuard guard;

    public SchoolWorkingDaysController(
            WorkingDaysService service,
            WorkingDaysGridService grid,
            WorkingDaysViewFactory views,
            WorkingDaysHistoryService history,
            YearCopyService copy,
            YearEntryService yearEntry,
            PermissionGuard guard) {
        this.service = service;
        this.grid = grid;
        this.views = views;
        this.history = history;
        this.copy = copy;
        this.yearEntry = yearEntry;
        this.guard = guard;
    }

    public record CreateRequest(UUID schoolId, Integer year, Integer month, BigDecimal workingDays) {}

    public record EditRequest(BigDecimal workingDays, Long version) {}

    public record CopyRequest(UUID schoolId, Integer fromYear, Integer toYear) {}

    @GetMapping
    public Page list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return grid.list(CallerContext.userId(jwt), CallerContext.roles(jwt), year, schoolId, zoneId, q, sort, page, size);
    }

    @GetMapping("/schools")
    public List<SchoolOption> schools(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return grid.options(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @PostMapping
    public ResponseEntity<RowView> create(@RequestBody CreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.CREATE);
        var row = service.create(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                request.schoolId(),
                request.year(),
                request.month(),
                request.workingDays());
        return ResponseEntity.status(201).body(views.view(row));
    }

    @PutMapping("/{id}")
    public RowView edit(@PathVariable UUID id, @RequestBody EditRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.EDIT);
        return views.view(service.edit(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.workingDays(), request.version()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestParam(required = false) Long version, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.DELETE);
        service.delete(CallerContext.userId(jwt), CallerContext.roles(jwt), id, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<Entry> rowHistory(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return history.forRow(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @GetMapping("/history")
    public List<Entry> keyHistory(
            @RequestParam UUID schoolId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return history.forKey(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId, year, month);
    }

    @GetMapping("/deleted")
    public List<DeletedEntry> deleted(
            @RequestParam UUID schoolId, @RequestParam int year, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return history.deleted(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId, year);
    }

    /** The twelve months of one School and year (spec 038 FR-001, FR-002). */
    @GetMapping("/year")
    public YearView year(
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.VIEW);
        return yearEntry.loadYear(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId, year);
    }

    /** Saves the changed months of a year; Create is needed per new month and Edit per stored month (spec 038 FR-020). */
    @PutMapping("/year")
    public SaveResult saveYear(@RequestBody SaveRequest request, @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, MODULE, PermissionAction.VIEW);
        boolean canCreate = guard.isGranted(roles, MODULE, PermissionAction.CREATE);
        boolean canEdit = guard.isGranted(roles, MODULE, PermissionAction.EDIT);
        if (!canCreate && !canEdit) {
            throw new AccessDeniedException("Not authorized for " + MODULE + " to create or edit");
        }
        return yearEntry.saveYear(CallerContext.userId(jwt), roles, request, canCreate, canEdit);
    }

    @PostMapping("/copy")
    public YearCopyService.Result copyYear(@RequestBody CopyRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), MODULE, PermissionAction.CREATE);
        return copy.copy(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                request.schoolId(),
                request.fromYear(),
                request.toYear());
    }
}
