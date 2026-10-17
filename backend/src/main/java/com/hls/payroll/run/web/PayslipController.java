package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.PayslipService;
import com.hls.payroll.run.internal.PayslipService.RunSlips;
import com.hls.payroll.run.internal.PayslipService.Slip;
import com.hls.school.api.CallerContext;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Payslip downloads of an approved run (spec 013b contracts/payroll-run-api.md). Always an attachment, never cached:
 * these carry pay data.
 */
@RestController
@RequestMapping("/api/v1/payroll/runs/{runId}")
public class PayslipController {

    private final PayslipService service;
    private final PermissionGuard guard;

    public PayslipController(PayslipService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/lines/{lineId}/payslip")
    public ResponseEntity<byte[]> payslip(
            @PathVariable UUID runId, @PathVariable UUID lineId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        Slip slip = service.one(runId, lineId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(slip.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(slip.bytes());
    }

    @GetMapping("/payslips")
    public ResponseEntity<StreamingResponseBody> payslips(@PathVariable UUID runId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        RunSlips slips = service.all(runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(slips.zipName()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(out -> service.writeZip(slips, out));
    }

    private static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName).build().toString();
    }
}
