package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.PersonHistoryService;
import com.hls.payroll.run.internal.PersonHistoryService.HistoryRow;
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The payroll history of one person across runs (spec 013b contracts/payroll-run-api.md). */
@RestController
@RequestMapping("/api/v1/payroll/people/{kind}/{personId}/history")
public class PayrollHistoryController {

    private final PersonHistoryService service;
    private final PermissionGuard guard;

    public PayrollHistoryController(PersonHistoryService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<HistoryRow> history(@PathVariable String kind, @PathVariable UUID personId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return service.history(kind, personId);
    }
}
