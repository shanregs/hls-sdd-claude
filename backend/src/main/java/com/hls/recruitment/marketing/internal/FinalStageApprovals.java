package com.hls.recruitment.marketing.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeView;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Final Stage prospects waiting for review (inbox kind {@code FINAL_STAGE_PROSPECT}), in the caller's Zones. */
@Component
class FinalStageApprovals implements ApprovalSource {

    static final String KIND = "FINAL_STAGE_PROSPECT";

    private final ProspectRepository prospects;
    private final EffectiveStageResolver stages;
    private final StageHistoryRepository history;
    private final MarketingScope scope;
    private final ProspectService prospectService;

    FinalStageApprovals(
            ProspectRepository prospects,
            EffectiveStageResolver stages,
            StageHistoryRepository history,
            MarketingScope scope,
            ProspectService prospectService) {
        this.prospects = prospects;
        this.stages = stages;
        this.history = history;
        this.scope = scope;
        this.prospectService = prospectService;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "MARKETING";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        ScopeView view = scope.of(userId, roles);
        List<Prospect> visible = prospects.findAll().stream().filter(p -> scope.allowsZone(view, p.getZoneId())).toList();
        Map<UUID, String> effective = stages.of(visible);
        List<Prospect> waiting = visible.stream().filter(p -> "FINAL_STAGE".equals(effective.get(p.getId()))).toList();
        Map<UUID, String> owners = prospectService.namesOf(
                waiting.stream().map(Prospect::getOwnerUserId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return waiting.stream()
                .map(p -> new ApprovalItem(
                        KIND, p.getId(), p.getOwnerUserId(), owners.getOrDefault(p.getOwnerUserId(), "Unknown user"),
                        p.getName() + " is at the Final Stage", enteredOn(p),
                        Link.to("/marketing/prospects/" + p.getId())))
                .toList();
    }

    private LocalDate enteredOn(Prospect p) {
        return history.findByProspectIdOrderByChangedAtAsc(p.getId()).stream()
                .filter(h -> StageHistory.STAGE.equals(h.getKind()) && h.getToStage() == ProspectStage.FINAL_STAGE)
                .map(StageHistory::getChangedAt)
                .max(Comparator.naturalOrder())
                .map(at -> LocalDate.ofInstant(at, ZoneOffset.UTC))
                .orElse(null);
    }
}
