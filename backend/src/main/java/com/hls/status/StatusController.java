package com.hls.status;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/status — per contracts/status-api.yaml. No authentication/authorization
 * is configured (FR-001); always returns HTTP 200, even when the reported status
 * is DEGRADED (FR-007) — the page itself must always load.
 */
@RestController
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/status")
    public ResponseEntity<StatusResponse> getStatus() {
        return ResponseEntity.ok(statusService.checkStatus());
    }
}
