package com.hls.identity.accessmodel;

import com.hls.identity.accessmodel.AccessModelDtos.AccessModelResponse;
import com.hls.identity.accessmodel.AccessModelDtos.NavItemView;
import com.hls.identity.accessmodel.AccessModelDtos.NavSection;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolves a principal's roles into the access model the frontend renders strictly from (FR-005,
 * FR-006): the union of their roles' authorized navigation, per-module actions, and data scope.
 * Recomputed fresh on every call — called at sign-in and every session renewal (research.md §7),
 * so a matrix edit takes effect within one renewal cycle (FR-015) with no separate cache to bust.
 */
@Service
public class AccessModelService {

    private final PermissionMatrixService permissionMatrixService;

    public AccessModelService(PermissionMatrixService permissionMatrixService) {
        this.permissionMatrixService = permissionMatrixService;
    }

    public AccessModelResponse resolve(Set<Role> callerRoles) {
        List<NavigationCatalog.NavItem> sortedCatalog = NavigationCatalog.ITEMS.stream()
                .sorted(Comparator.comparingInt(NavigationCatalog.NavItem::order))
                .toList();

        Map<String, List<NavItemView>> itemsBySection = new LinkedHashMap<>();
        for (NavigationCatalog.NavItem item : sortedCatalog) {
            Set<Role> matchingRoles = EnumSet.noneOf(Role.class);
            for (Role role : item.applicableRoles()) {
                if (callerRoles.contains(role)) {
                    matchingRoles.add(role);
                }
            }
            if (matchingRoles.isEmpty()) {
                continue;
            }
            boolean visible = matchingRoles.stream()
                    .anyMatch(role -> permissionMatrixService.isGranted(role, item.module(), item.action()));
            if (!visible) {
                continue;
            }

            List<String> actions = grantedActions(matchingRoles, item.module());
            itemsBySection
                    .computeIfAbsent(item.section(), key -> new ArrayList<>())
                    .add(new NavItemView(item.label(), item.route(), actions));
        }

        List<NavSection> navigation =
                itemsBySection.entrySet().stream().map(e -> new NavSection(e.getKey(), e.getValue())).toList();

        Map<String, String> dataScope = new LinkedHashMap<>();
        DataScope widestDashboardScope = callerRoles.stream()
                .filter(role -> permissionMatrixService.isGranted(role, PermissionModule.DASHBOARD, PermissionAction.VIEW))
                .map(this::dashboardScopeFor)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
        if (widestDashboardScope != null) {
            dataScope.put(PermissionModule.DASHBOARD.name(), widestDashboardScope.name());
        }

        List<String> roleNames = callerRoles.stream().map(Enum::name).sorted().toList();
        return new AccessModelResponse(roleNames, navigation, dataScope);
    }

    private List<String> grantedActions(Set<Role> roles, PermissionModule module) {
        List<String> actions = new ArrayList<>();
        for (PermissionAction action : PermissionAction.values()) {
            boolean granted = roles.stream().anyMatch(role -> permissionMatrixService.isGranted(role, module, action));
            if (granted) {
                actions.add(action.name());
            }
        }
        return actions;
    }

    /** Only Dashboard carries a meaningful scope today (data-model.md); Constitution Principle III. */
    private DataScope dashboardScopeFor(Role role) {
        return switch (role) {
            case ADMIN, DIRECTOR -> DataScope.ORG_WIDE;
            case MANAGER -> DataScope.ASSIGNED;
            case TEACHER -> DataScope.OWN;
            case SYSTEM -> DataScope.NONE;
        };
    }
}
