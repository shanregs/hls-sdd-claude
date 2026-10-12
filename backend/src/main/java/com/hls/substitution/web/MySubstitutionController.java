package com.hls.substitution.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.substitution.internal.SubstitutionService;
import com.hls.substitution.internal.SubstitutionService.MyView;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The days the signed-in Teacher was assigned to cover (spec 017a US4). The Teacher is always the caller from the JWT. */
@RestController
@RequestMapping("/api/v1/me/substitutions")
public class MySubstitutionController {

    private final SubstitutionService service;
    private final PermissionGuard guard;

    public MySubstitutionController(SubstitutionService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<MyView> mine(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_SUBSTITUTIONS, PermissionAction.VIEW);
        return service.mine(
                CallerContext.userId(jwt),
                SubstitutionController.parseDate(from, "from"),
                SubstitutionController.parseDate(to, "to"));
    }
}
