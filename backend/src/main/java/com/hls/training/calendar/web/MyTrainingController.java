package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.UpcomingService;
import com.hls.training.calendar.internal.UpcomingService.Upcoming;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The own upcoming training of the caller (spec 024 contracts/training-api.md): a Teacher with registration View or a Manager with training View. */
@RestController("trainingMyTrainingController")
@RequestMapping("/api/v1/training/me")
public class MyTrainingController {

    private final UpcomingService upcoming;
    private final PermissionGuard guard;

    public MyTrainingController(UpcomingService upcoming, PermissionGuard guard) {
        this.upcoming = upcoming;
        this.guard = guard;
    }

    @GetMapping("/upcoming")
    public List<Upcoming> upcoming(@AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        boolean allowed = guard.isGranted(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.VIEW)
                || guard.isGranted(roles, PermissionModule.TRAINING, PermissionAction.VIEW);
        if (!allowed) {
            throw new AccessDeniedException("Not authorized for training");
        }
        return upcoming.mine(new Caller(CallerContext.userId(jwt), roles, grants(roles)));
    }

    private Caller.Grants grants(Set<Role> roles) {
        return new Caller.Grants(
                guard.isGranted(roles, PermissionModule.TRAINING, PermissionAction.VIEW),
                false,
                false,
                false,
                false,
                false,
                false,
                guard.isGranted(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.VIEW),
                guard.isGranted(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.CREATE),
                guard.isGranted(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.EDIT));
    }
}
