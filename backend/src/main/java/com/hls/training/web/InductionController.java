package com.hls.training.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.training.internal.BatchService;
import com.hls.training.internal.BatchService.BatchDto;
import com.hls.training.internal.BatchService.BatchRequest;
import com.hls.training.internal.BatchService.EnrolRequest;
import com.hls.training.internal.BatchService.RecruitRef;
import com.hls.training.internal.InductionAttendanceService;
import com.hls.training.internal.InductionAttendanceService.AttendanceRequest;
import com.hls.training.internal.InductionAttendanceService.DayRow;
import com.hls.training.internal.InductionAttendanceService.RosterRow;
import com.hls.training.internal.SignOffService;
import com.hls.training.internal.SignOffService.EnrolmentDto;
import com.hls.training.internal.SignOffService.FollowUpRequest;
import com.hls.training.internal.SignOffService.SignOffRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Induction batches, attendance and sign-off (module {@code INDUCTION}); Admin and Director by default. */
@RestController
@RequestMapping("/api/v1/induction")
public class InductionController {

    private final BatchService batches;
    private final InductionAttendanceService attendance;
    private final SignOffService signOffs;
    private final PermissionGuard guard;

    public InductionController(
            BatchService batches, InductionAttendanceService attendance, SignOffService signOffs, PermissionGuard guard) {
        this.batches = batches;
        this.attendance = attendance;
        this.signOffs = signOffs;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.INDUCTION, action);
    }

    @GetMapping("/batches")
    public List<BatchDto> batches(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return batches.list();
    }

    @PostMapping("/batches")
    public ResponseEntity<BatchDto> create(@RequestBody BatchRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(batches.create(CallerContext.userId(jwt), request));
    }

    @PostMapping("/batches/{id}/cancel")
    public BatchDto cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return batches.cancel(CallerContext.userId(jwt), id);
    }

    @PostMapping("/batches/{id}/enrol")
    public ResponseEntity<EnrolmentDto> enrol(
            @PathVariable UUID id, @RequestBody EnrolRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        var enrolment = batches.enrol(CallerContext.userId(jwt), id, request.teacherId());
        return ResponseEntity.status(201)
                .body(new EnrolmentDto(
                        enrolment.getId(), enrolment.getBatchId(), enrolment.getTeacherId(), null, null, null));
    }

    @GetMapping("/batches/{id}/roster")
    public List<RosterRow> roster(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return attendance.roster(id);
    }

    @PostMapping("/enrolments/{id}/attendance")
    public DayRow attendance(
            @PathVariable UUID id, @RequestBody AttendanceRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return attendance.record(CallerContext.userId(jwt), id, request);
    }

    @PostMapping("/enrolments/{id}/signoff")
    public EnrolmentDto signOff(
            @PathVariable UUID id, @RequestBody SignOffRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return signOffs.signOff(CallerContext.userId(jwt), id, request);
    }

    @PostMapping("/enrolments/{id}/follow-up")
    public EnrolmentDto followUp(
            @PathVariable UUID id, @RequestBody FollowUpRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return signOffs.followUp(CallerContext.userId(jwt), id, request);
    }

    @GetMapping("/ready-to-deploy")
    public List<RecruitRef> readyToDeploy(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return batches.readyToDeploy();
    }

    @GetMapping("/to-be-enrolled")
    public List<RecruitRef> toBeEnrolled(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return batches.toBeEnrolled();
    }
}
