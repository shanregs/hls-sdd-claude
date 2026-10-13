package com.hls.attendance.manager.web;

import com.hls.attendance.manager.internal.ActivityLink;
import com.hls.attendance.manager.internal.CheckInPhotoService;
import com.hls.attendance.manager.internal.CheckInService;
import com.hls.attendance.manager.internal.CheckInService.LocationInput;
import com.hls.attendance.manager.internal.ManagerDayView;
import com.hls.attendance.manager.internal.ManagerGridService;
import com.hls.attendance.manager.internal.RegularizationService;
import com.hls.attendance.manager.internal.RegularizationService.RegularizationView;
import com.hls.attendance.manager.internal.RegularizationService.RequestInput;
import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A Manager's own day (spec 032 US1): today's status, check-in, check-out, the optional photo and the completion of a
 * missing check-out. The Manager is always the caller's own record, resolved from the JWT.
 */
@RestController
@RequestMapping("/api/v1/manager-attendance/me")
public class MyManagerAttendanceController {

    public record ActivityRef(String kind, UUID id) {}

    public record CheckInRequest(
            String dayType,
            String purpose,
            List<ActivityRef> activities,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters) {}

    public record CheckOutRequest(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {}

    public record CompleteRequest(String time, String reason) {}

    private final CheckInService checkIns;
    private final CheckInPhotoService photos;
    private final ManagerGridService grid;
    private final RegularizationService regularizations;
    private final PermissionGuard guard;

    public MyManagerAttendanceController(
            CheckInService checkIns,
            CheckInPhotoService photos,
            ManagerGridService grid,
            RegularizationService regularizations,
            PermissionGuard guard) {
        this.checkIns = checkIns;
        this.photos = photos;
        this.grid = grid;
        this.regularizations = regularizations;
        this.guard = guard;
    }

    @GetMapping("/today")
    public CheckInService.Today today(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return checkIns.today(CallerContext.userId(jwt));
    }

    @GetMapping("/months/{month}")
    public ManagerGridService.MonthView month(@PathVariable String month, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return grid.myMonth(CallerContext.userId(jwt), ManagerGridService.parseMonth(month));
    }

    @GetMapping("/regularizations")
    public List<RegularizationView> regularizations(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return regularizations.mine(CallerContext.userId(jwt));
    }

    @PostMapping("/regularizations")
    public ResponseEntity<RegularizationView> requestRegularization(
            @RequestBody RequestInput body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(regularizations.request(CallerContext.userId(jwt), body));
    }

    @DeleteMapping("/regularizations/{id}")
    public ResponseEntity<Void> cancelRegularization(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.DELETE);
        regularizations.cancel(CallerContext.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/check-in")
    public ResponseEntity<ManagerDayView> checkIn(@RequestBody CheckInRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        LocationInput where = new LocationInput(body.latitude(), body.longitude(), body.accuracyMeters());
        return ResponseEntity.status(201).body(checkIns.checkIn(CallerContext.userId(jwt), body.dayType(), body.purpose(), links(body.activities()), where));
    }

    private static List<ActivityLink> links(List<ActivityRef> refs) {
        return refs == null
                ? List.of()
                : refs.stream().map(r -> new ActivityLink(r.kind(), r.id())).toList();
    }

    @PostMapping("/check-out")
    public ManagerDayView checkOut(@RequestBody CheckOutRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return checkIns.checkOut(
                CallerContext.userId(jwt), new LocationInput(body.latitude(), body.longitude(), body.accuracyMeters()));
    }

    @PostMapping("/days/{date}/complete-check-out")
    public ManagerDayView complete(
            @PathVariable LocalDate date, @RequestBody CompleteRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        UUID userId = CallerContext.userId(jwt);
        return checkIns.completeCheckOut(userId, photos.ownManagerId(userId), date, parseTime(body.time()), body.reason());
    }

    @PostMapping("/days/{date}/photo")
    public ResponseEntity<ManagerDayView> photo(
            @PathVariable LocalDate date, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        try {
            return ResponseEntity.status(201)
                    .body(photos.attach(CallerContext.userId(jwt), date, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
    }

    @GetMapping("/days/{date}/photo")
    public ResponseEntity<byte[]> openPhoto(@PathVariable LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return download(photos.open(photos.ownManagerId(CallerContext.userId(jwt)), date));
    }

    static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The check-out time is required.");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The time must look like 18:05.");
        }
    }

    /** Always sent as an attachment with {@code nosniff}, never shown inline. */
    static ResponseEntity<byte[]> download(FileContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_MANAGER_ATTENDANCE, action);
    }
}
