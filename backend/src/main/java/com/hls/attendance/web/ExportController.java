package com.hls.attendance.web;

import com.hls.attendance.internal.AttendanceGridService;
import com.hls.attendance.internal.CsvExporter;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CSV export of the filtered month grid for Admin and Director (spec 008 US8). Needs {@code ATTENDANCE.EXPORT}. */
@RestController
@RequestMapping("/api/v1/attendance")
public class ExportController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final CsvExporter exporter;
    private final PermissionGuard guard;

    public ExportController(CsvExporter exporter, PermissionGuard guard) {
        this.exporter = exporter;
        this.guard = guard;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String month,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.EXPORT);
        CsvExporter.Export file = exporter.export(
                CallerContext.userId(jwt),
                roles,
                MonthParam.parse(month),
                new AttendanceGridService.Filter(query, zoneId, schoolId, managerId, status));
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
