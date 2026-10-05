package com.hls.recruitment.marketing.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.ActivityService;
import com.hls.recruitment.marketing.internal.ActivityService.ActivityDto;
import com.hls.recruitment.marketing.internal.ActivityService.AttachmentRef;
import com.hls.recruitment.marketing.internal.ActivityService.CancelRequest;
import com.hls.recruitment.marketing.internal.ActivityService.CompleteRequest;
import com.hls.recruitment.marketing.internal.ActivityService.ListDto;
import com.hls.recruitment.marketing.internal.ActivityService.PlanRequest;
import com.hls.recruitment.marketing.internal.ActivityService.RescheduleRequest;
import com.hls.recruitment.marketing.internal.AttachmentService;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Visits, calls and meetings, with their attachments (module {@code MARKETING}). */
@RestController
@RequestMapping("/api/v1/marketing")
public class ActivityController {

    private final ActivityService activities;
    private final AttachmentService attachments;
    private final PermissionGuard guard;

    public ActivityController(ActivityService activities, AttachmentService attachments, PermissionGuard guard) {
        this.activities = activities;
        this.attachments = attachments;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING, action);
    }

    @GetMapping("/activities")
    public ListDto list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) UUID prospect,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return activities.list(CallerContext.userId(jwt), CallerContext.roles(jwt), from, to, mine, prospect);
    }

    @GetMapping("/activities/{id}")
    public ActivityDto get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return activities.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping("/activities")
    public ResponseEntity<ActivityDto> plan(@RequestBody PlanRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(activities.plan(CallerContext.userId(jwt), CallerContext.roles(jwt), request));
    }

    @PostMapping("/activities/{id}/complete")
    public ActivityDto complete(@PathVariable UUID id, @RequestBody CompleteRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return activities.complete(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/activities/{id}/reschedule")
    public ActivityDto reschedule(@PathVariable UUID id, @RequestBody RescheduleRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return activities.reschedule(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/activities/{id}/cancel")
    public ActivityDto cancel(@PathVariable UUID id, @RequestBody CancelRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return activities.cancel(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/activities/{id}/attachments")
    public ResponseEntity<AttachmentRef> upload(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        try {
            return ResponseEntity.status(201)
                    .body(attachments.upload(
                            CallerContext.userId(jwt), CallerContext.roles(jwt), id, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
    }

    public record RemoveRequest(String reason) {}

    @DeleteMapping("/activities/{id}/attachments/{fileId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID id, @PathVariable UUID fileId, @RequestBody RemoveRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        attachments.remove(CallerContext.userId(jwt), CallerContext.roles(jwt), id, fileId, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }
}
