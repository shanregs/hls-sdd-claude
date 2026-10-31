package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.PeopleSearch;
import com.hls.training.calendar.internal.PeopleSearch.Found;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Person picker for individual assignment and walk-ins (spec 024 contracts/training-api.md, People search). */
@RestController("trainingPeopleSearchController")
@RequestMapping("/api/v1/training/people")
public class PeopleSearchController {

    private final PeopleSearch search;
    private final PermissionGuard guard;

    public PeopleSearchController(PeopleSearch search, PermissionGuard guard) {
        this.search = search;
        this.guard = guard;
    }

    /** At most 20 people within the caller's scope; needs {@code TRAINING} Edit (assign) or {@code TRAINING_ATTENDANCE} Edit (walk-in). */
    @GetMapping
    public List<Found> find(
            @RequestParam(required = false) String kind, @RequestParam(required = false) String q, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        if (!guard.isGranted(roles, PermissionModule.TRAINING, PermissionAction.EDIT)
                && !guard.isGranted(roles, PermissionModule.TRAINING_ATTENDANCE, PermissionAction.EDIT)) {
            throw new AccessDeniedException("Not authorized for training");
        }
        String parsed = kind == null || kind.isBlank() ? null : kind.toUpperCase(Locale.ROOT);
        return search.search(new Caller(CallerContext.userId(jwt), roles), parsed, q);
    }
}
