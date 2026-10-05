package com.hls.recruitment.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.internal.OfferService;
import com.hls.recruitment.internal.OfferService.AcceptRequest;
import com.hls.recruitment.internal.OfferService.DeclineRequest;
import com.hls.recruitment.internal.OfferService.OfferDto;
import com.hls.recruitment.internal.OfferService.OfferRequest;
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RestController;

/** Job offers (module {@code OFFERS}): every role with VIEW sees all; only the roles granted write act on them. */
@RestController
@RequestMapping("/api/v1/recruitment")
public class OfferController {

    private final OfferService offers;
    private final PermissionGuard guard;

    public OfferController(OfferService offers, PermissionGuard guard) {
        this.offers = offers;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.OFFERS, action);
    }

    @GetMapping("/offers")
    public List<OfferDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "candidate", required = false) UUID candidate,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return offers.list(status, candidate);
    }

    @PostMapping("/candidates/{id}/offers")
    public ResponseEntity<OfferDto> create(
            @PathVariable UUID id, @RequestBody OfferRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(offers.createDraft(CallerContext.userId(jwt), id, request));
    }

    @PutMapping("/offers/{id}")
    public OfferDto update(@PathVariable UUID id, @RequestBody OfferRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return offers.updateDraft(CallerContext.userId(jwt), id, request);
    }

    @PostMapping("/offers/{id}/issue")
    public OfferDto issue(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return offers.issue(CallerContext.userId(jwt), id);
    }

    @PostMapping("/offers/{id}/supersede")
    public ResponseEntity<OfferDto> supersede(
            @PathVariable UUID id, @RequestBody OfferRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(offers.supersede(CallerContext.userId(jwt), id, request));
    }

    @PostMapping("/offers/{id}/accept")
    public OfferDto accept(
            @PathVariable UUID id, @RequestBody(required = false) AcceptRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return offers.accept(CallerContext.userId(jwt), id, request);
    }

    @PostMapping("/offers/{id}/decline")
    public OfferDto decline(
            @PathVariable UUID id, @RequestBody DeclineRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return offers.decline(CallerContext.userId(jwt), id, request);
    }

    @GetMapping(value = "/offers/{id}/letter", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> letter(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .contentType(MediaType.TEXT_HTML)
                .body(offers.letterFor(id));
    }
}
