package com.hls.performance.web;

import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.performance.internal.Caller;
import com.hls.performance.internal.ObservationService;
import com.hls.performance.internal.ObservationService.AttachmentView;
import com.hls.performance.internal.ObservationService.NotHeldRequest;
import com.hls.performance.internal.ObservationService.ObservationView;
import com.hls.performance.internal.ObservationService.PreviewRequest;
import com.hls.performance.internal.ObservationService.SaveRequest;
import com.hls.performance.internal.PerformanceQueries;
import com.hls.performance.internal.PerformanceQueries.Filter;
import com.hls.performance.internal.PerformanceQueries.ObservationRow;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/** Classroom observations (contracts/performance-api.md), gated by the TEACHER_PERFORMANCE grants and the scope of the Teacher. */
@RestController
@RequestMapping("/api/v1/teacher-performance/observations")
public class ObservationController {

    private final ObservationService service;
    private final PerformanceQueries queries;
    private final PermissionGuard guard;

    public ObservationController(ObservationService service, PerformanceQueries queries, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.guard = guard;
    }

    public record StartBody(UUID teacherId, LocalDate observedOn) {}

    public record CorrectBody(String reason, SaveRequest form) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_PERFORMANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public PageResponse<ObservationRow> list(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Boolean principalDeclined,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.observations(caller(jwt), new Filter(zoneId, schoolId, managerId, teacherId, result, status, from, to, principalDeclined), page, size);
    }

    @PostMapping("/preview")
    public ObservationService.Preview preview(@RequestBody PreviewRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.preview(body);
    }

    @PostMapping
    public ResponseEntity<ObservationView> start(@RequestBody StartBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(service.start(caller(jwt), body.teacherId(), body.observedOn()));
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean versions, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        if (versions) {
            return service.versions(caller(jwt), id);
        }
        return service.get(caller(jwt), id);
    }

    @PutMapping("/{id}")
    public ObservationView save(@PathVariable UUID id, @RequestBody SaveRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.save(caller(jwt), id, body);
    }

    @PostMapping("/{id}/submit")
    public ObservationView submit(@PathVariable UUID id, @RequestBody(required = false) SaveRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.submit(caller(jwt), id, body);
    }

    @PostMapping("/{id}/not-held")
    public ObservationView notHeld(@PathVariable UUID id, @RequestBody NotHeldRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.notHeld(caller(jwt), id, body);
    }

    @PostMapping("/{id}/correct")
    public ObservationView correct(@PathVariable UUID id, @RequestBody CorrectBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        if (body == null || body.form() == null) {
            throw new InvalidInputException("The corrected form is required.");
        }
        return service.correct(caller(jwt), id, body.reason(), body.form());
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<AttachmentView> attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        try {
            return ResponseEntity.status(201).body(service.addAttachment(caller(jwt), id, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
    }

    @DeleteMapping("/{id}/attachments/{fileId}")
    public ResponseEntity<Void> removeAttachment(
            @PathVariable UUID id, @PathVariable UUID fileId, @RequestParam String reason, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        service.removeAttachment(caller(jwt), id, fileId, reason);
        return ResponseEntity.noContent().build();
    }

    /** Always sent as an attachment with {@code nosniff}, never shown inline. */
    @GetMapping("/{id}/attachments/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        FileContent content = service.download(caller(jwt), id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}
