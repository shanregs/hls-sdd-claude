package com.hls.schoolbilling.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.schoolbilling.internal.DueAdjustmentService;
import com.hls.schoolbilling.internal.BillingSettings;
import com.hls.schoolbilling.internal.CollectionService;
import com.hls.schoolbilling.internal.DueService;
import com.hls.schoolbilling.internal.LedgerQueryService;
import com.hls.schoolbilling.internal.ReceivablesDtos.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** School Billing HTTP API (contracts/receivables-api.md). Every endpoint re-checks grant and scope on the server. */
@RestController
@RequestMapping("/api/v1/school-billing")
public class ReceivablesController {

    private static final PermissionModule MODULE = PermissionModule.SCHOOL_BILLING;

    private final PermissionGuard guard;
    private final DueService dues;
    private final DueAdjustmentService adjustments;
    private final CollectionService collections;
    private final LedgerQueryService queries;
    private final BillingSettings settings;

    public ReceivablesController(
            PermissionGuard guard,
            DueService dues,
            DueAdjustmentService adjustments,
            CollectionService collections,
            LedgerQueryService queries,
            BillingSettings settings) {
        this.guard = guard;
        this.dues = dues;
        this.adjustments = adjustments;
        this.collections = collections;
        this.queries = queries;
        this.settings = settings;
    }

    private BillingCaller require(Jwt jwt, PermissionAction action) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, MODULE, action);
        return new BillingCaller(CallerContext.userId(jwt), roles);
    }

    private static YearMonth month(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidInputException("The month must look like 2026-11.");
        }
    }

    @GetMapping("/overview")
    public Overview overview(
            @RequestParam String month,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID schoolId,
            @AuthenticationPrincipal Jwt jwt) {
        return queries.overview(require(jwt, PermissionAction.VIEW), month(month), zoneId, managerId, schoolId);
    }

    @GetMapping("/ageing")
    public Ageing ageing(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID managerId,
            @AuthenticationPrincipal Jwt jwt) {
        return queries.ageing(require(jwt, PermissionAction.VIEW), asOf, zoneId, managerId);
    }

    @GetMapping("/not-billed")
    public List<NotBilledRow> notBilled(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        return queries.notBilled(require(jwt, PermissionAction.VIEW), month(month));
    }

    @PostMapping("/dues/raise")
    public RaiseResult raise(@RequestBody RaiseBody body, @AuthenticationPrincipal Jwt jwt) {
        BillingCaller caller = require(jwt, PermissionAction.EDIT);
        if (body == null || body.month() == null) {
            throw new InvalidInputException("The month is required.");
        }
        return dues.raiseAll(caller, body.month(), body.schoolIds());
    }

    @GetMapping("/dues")
    public PageResponse<DueRow> dues(
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        BillingCaller caller = require(jwt, PermissionAction.VIEW);
        return queries.dues(caller, schoolId, month == null || month.isBlank() ? null : month(month), status, page, size);
    }

    @GetMapping("/dues/{id}")
    public DueDetail due(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queries.detail(require(jwt, PermissionAction.VIEW), id);
    }

    @PostMapping("/dues/{id}/cancel")
    public DueRow cancel(@PathVariable UUID id, @RequestBody CancelBody body, @AuthenticationPrincipal Jwt jwt) {
        return dues.cancel(require(jwt, PermissionAction.APPROVE), id, body == null ? null : body.reason());
    }

    @PostMapping("/dues/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView addAdjustment(@PathVariable UUID id, @RequestBody AdjustmentBody body, @AuthenticationPrincipal Jwt jwt) {
        BillingCaller caller = require(jwt, PermissionAction.EDIT);
        return adjustments.add(caller, id, body == null ? null : body.amount(), body == null ? null : body.reason());
    }

    @PostMapping("/adjustments/{id}/reverse")
    public AdjustmentView reverseAdjustment(@PathVariable UUID id, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        return adjustments.reverse(require(jwt, PermissionAction.EDIT), id, body == null ? null : body.reason());
    }

    @GetMapping("/schools/{schoolId}/ledger")
    public SchoolLedger ledger(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        return queries.schoolLedger(require(jwt, PermissionAction.VIEW), schoolId);
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionView record(@RequestBody CollectionBody body, @AuthenticationPrincipal Jwt jwt) {
        return collections.record(require(jwt, PermissionAction.CREATE), body);
    }

    @PostMapping("/collections/{id}/allocate")
    public CollectionView allocate(@PathVariable UUID id, @RequestBody(required = false) AllocateBody body, @AuthenticationPrincipal Jwt jwt) {
        return collections.allocate(require(jwt, PermissionAction.CREATE), id, body == null ? null : body.allocations());
    }

    @PostMapping("/collections/{id}/reverse")
    public CollectionView reverseCollection(@PathVariable UUID id, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        return collections.reverse(require(jwt, PermissionAction.EDIT), id, body == null ? null : body.reason());
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam String view,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID schoolId,
            @AuthenticationPrincipal Jwt jwt) {
        BillingCaller caller = require(jwt, PermissionAction.EXPORT);
        String body =
                switch (view) {
                    case "overview" -> queries.exportOverview(caller, month(month), zoneId, managerId, schoolId);
                    case "ageing" -> queries.exportAgeing(caller, asOf, zoneId, managerId);
                    case "ledger" -> {
                        if (schoolId == null) {
                            throw new InvalidInputException("The School is required.");
                        }
                        yield queries.exportLedger(caller, schoolId);
                    }
                    default -> throw new InvalidInputException("The view must be overview, ageing or ledger.");
                };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"school-billing-" + view + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/settings")
    public SettingsView settings(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return new SettingsView(settings.paymentDueDays(), settings.escalationDays());
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(@RequestBody SettingsBody body, @AuthenticationPrincipal Jwt jwt) {
        BillingCaller caller = require(jwt, PermissionAction.EDIT);
        settings.update(caller.userId(), body == null ? null : body.paymentDueDays(), body == null ? null : body.escalationDays());
        return new SettingsView(settings.paymentDueDays(), settings.escalationDays());
    }
}
