package com.hls.notification.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.notification.internal.NotificationService;
import com.hls.notification.internal.NotificationService.ListResponse;
import com.hls.notification.internal.NotificationService.NotificationView;
import com.hls.school.api.CallerContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own notifications (spec 010). The recipient is always the caller from the JWT, so
 * no request can name another user; a foreign notification id is answered as not found.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService service;
    private final PermissionGuard guard;

    public NotificationController(NotificationService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ListResponse list(
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        view(jwt);
        return service.list(CallerContext.userId(jwt), unread, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        view(jwt);
        return Map.of("unread", service.unreadCount(CallerContext.userId(jwt)));
    }

    @PostMapping("/{id}/read")
    public NotificationView read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        view(jwt);
        return service.markRead(CallerContext.userId(jwt), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal Jwt jwt) {
        view(jwt);
        return Map.of("marked", service.markAllRead(CallerContext.userId(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        delete(jwt);
        service.delete(CallerContext.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/read")
    public Map<String, Integer> clearRead(@AuthenticationPrincipal Jwt jwt) {
        delete(jwt);
        return Map.of("deleted", service.clearRead(CallerContext.userId(jwt)));
    }

    private void view(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.NOTIFICATIONS, PermissionAction.VIEW);
    }

    private void delete(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.NOTIFICATIONS, PermissionAction.DELETE);
    }
}
