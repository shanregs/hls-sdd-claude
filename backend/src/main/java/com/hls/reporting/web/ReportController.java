package com.hls.reporting.web;

import com.hls.reporting.api.ReportPage;
import com.hls.reporting.internal.report.ReportService;
import com.hls.school.api.CallerContext;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reports (spec 014a): the menu for the caller, one report page, and its CSV. Access is per report through the permission
 * matrix (View, Export) and the scope is applied on the server before anything is counted.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public ReportService.Menu menu(@AuthenticationPrincipal Jwt jwt) {
        return service.menu(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping("/{key}")
    public ReportPage report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String key,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String managerId) {
        return service.read(CallerContext.userId(jwt), CallerContext.roles(jwt), key, month, zoneId, schoolId, teacherId, managerId);
    }

    @GetMapping("/{key}/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String key,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String managerId) {
        ReportService.CsvFile file = service.export(
                CallerContext.userId(jwt), CallerContext.roles(jwt), key, month, zoneId, schoolId, teacherId, managerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }
}
