package com.hls.expenses.web;

import com.hls.expenses.internal.BillService;
import com.hls.expenses.internal.ClaimService;
import com.hls.expenses.internal.ClaimService.ClaimRequest;
import com.hls.expenses.internal.ClaimService.Created;
import com.hls.expenses.internal.ClaimService.LinkChoice;
import com.hls.expenses.internal.ClaimViews.Detail;
import com.hls.expenses.internal.ExpenseCaller;
import com.hls.expenses.internal.MyExpensesQueries;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

/** My Expenses (contracts/expenses-api.md): a claimant raises and tracks their own claims. Every grant is MY_EXPENSES. */
@RestController
@RequestMapping("/api/v1/my-expenses")
public class MyExpensesController {

    private final ClaimService service;
    private final BillService billService;
    private final MyExpensesQueries queries;
    private final PermissionGuard guard;

    public MyExpensesController(ClaimService service, BillService billService, MyExpensesQueries queries, PermissionGuard guard) {
        this.service = service;
        this.billService = billService;
        this.queries = queries;
        this.guard = guard;
    }

    public record ClaimBody(
            UUID clientRequestId,
            LocalDate expenseDate,
            BigDecimal amount,
            String category,
            UUID zoneId,
            String note,
            String paidBy,
            String reason,
            String noBillReason,
            LinkChoice link,
            Boolean confirmDuplicate,
            Boolean submit,
            Long version) {

        ClaimRequest toRequest() {
            return new ClaimRequest(
                    clientRequestId, expenseDate, amount, category, zoneId, note, paidBy, reason, noBillReason, link,
                    confirmDuplicate, submit, version);
        }
    }

    public record SubmitBody(Long version, Boolean confirmDuplicate) {}

    public record VersionBody(Long version) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_EXPENSES, action);
    }

    private static ExpenseCaller caller(Jwt jwt) {
        return new ExpenseCaller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public MyExpensesQueries.MyList list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.list(caller(jwt), status, month, page, size);
    }

    @GetMapping("/areas")
    public List<MyExpensesQueries.Area> areas(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.areas();
    }

    @GetMapping("/link-options")
    public List<MyExpensesQueries.LinkOption> linkOptions(
            @RequestParam String category, @RequestParam(required = false) LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.linkOptions(caller(jwt), category, date);
    }

    @PostMapping
    public ResponseEntity<Detail> create(@RequestBody ClaimBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        ExpenseCaller caller = caller(jwt);
        Created created = service.create(caller, body.toRequest());
        return ResponseEntity.status(created.created() ? 201 : 200).body(queries.detail(caller, created.id()));
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(caller(jwt), id);
    }

    @PutMapping("/{id}")
    public Detail update(@PathVariable UUID id, @RequestBody ClaimBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        ExpenseCaller caller = caller(jwt);
        service.edit(caller, id, body.toRequest());
        return queries.detail(caller, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.DELETE);
        service.delete(caller(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public Detail submit(@PathVariable UUID id, @RequestBody SubmitBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        ExpenseCaller caller = caller(jwt);
        service.submit(caller, id, body == null ? null : body.version(), body != null && Boolean.TRUE.equals(body.confirmDuplicate()));
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/withdraw")
    public Detail withdraw(@PathVariable UUID id, @RequestBody(required = false) VersionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        ExpenseCaller caller = caller(jwt);
        service.withdraw(caller, id, body == null ? null : body.version());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/bills")
    public ResponseEntity<Detail> addBill(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        ExpenseCaller caller = caller(jwt);
        try {
            billService.add(caller, id, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    @GetMapping("/{id}/bills/{billId}")
    public ResponseEntity<byte[]> downloadBill(@PathVariable UUID id, @PathVariable UUID billId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        queries.detail(caller(jwt), id); // own claim only; anyone else sees "not found"
        return BillResponses.download(billService.open(id, billId));
    }
}
