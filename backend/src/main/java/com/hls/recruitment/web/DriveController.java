package com.hls.recruitment.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.internal.CollegeService;
import com.hls.recruitment.internal.CollegeService.CollegeDto;
import com.hls.recruitment.internal.CollegeService.CollegeRequest;
import com.hls.recruitment.internal.DriveService;
import com.hls.recruitment.internal.DriveService.DriveDto;
import com.hls.recruitment.internal.DriveService.DriveRequest;
import com.hls.recruitment.internal.DriveService.StatusRequest;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Colleges and campus drives (module {@code RECRUITMENT}); a Zone Manager writes only to own drives. */
@RestController
@RequestMapping("/api/v1/recruitment")
public class DriveController {

    private final CollegeService colleges;
    private final DriveService drives;
    private final PermissionGuard guard;

    public DriveController(CollegeService colleges, DriveService drives, PermissionGuard guard) {
        this.colleges = colleges;
        this.drives = drives;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.RECRUITMENT, action);
    }

    @GetMapping("/colleges")
    public List<CollegeDto> colleges(@RequestParam(required = false) String query, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return colleges.list(query);
    }

    @PostMapping("/colleges")
    public ResponseEntity<CollegeDto> addCollege(@RequestBody CollegeRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(colleges.create(CallerContext.userId(jwt), request));
    }

    @PutMapping("/colleges/{id}")
    public CollegeDto updateCollege(
            @PathVariable UUID id, @RequestBody CollegeRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return colleges.update(CallerContext.userId(jwt), id, request);
    }

    @GetMapping("/interviewers")
    public List<DriveService.PersonRef> interviewers(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return drives.interviewerChoices();
    }

    @GetMapping("/drives")
    public List<DriveDto> drives(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String season,
            @RequestParam(defaultValue = "false") boolean mine,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return drives.list(CallerContext.userId(jwt), from, to, season, mine);
    }

    @PostMapping("/drives")
    public ResponseEntity<DriveDto> schedule(@RequestBody DriveRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(drives.create(CallerContext.userId(jwt), request));
    }

    @GetMapping("/drives/{id}")
    public DriveDto drive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return drives.get(id);
    }

    @PutMapping("/drives/{id}")
    public DriveDto update(@PathVariable UUID id, @RequestBody DriveRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return drives.update(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/drives/{id}/status")
    public DriveDto status(@PathVariable UUID id, @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return drives.setStatus(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }
}
