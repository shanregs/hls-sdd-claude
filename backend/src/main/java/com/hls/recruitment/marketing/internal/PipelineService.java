package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sales pipeline: moves between the seven active stages, on hold and lost (with reasons, resumed or reopened to the
 * stage left), and the Final Stage review. MoU and Active are never set here: they follow the contract in spec 012.
 */
@Service
public class PipelineService {

    public record StageRequest(String stage, String reason, Long version) {}

    public record ReviewRequest(String decision, String reason) {}

    public record Board(Map<String, List<ProspectService.ProspectRow>> columns, Map<String, Integer> counts) {}

    private static final List<String> COLUMNS =
            List.of("PROSPECT", "CONTACTED", "VISIT", "FOLLOW_UP", "INTERESTED", "NEGOTIATION", "FINAL_STAGE", "WON", "MOU", "ACTIVE", "ON_HOLD", "LOST");

    private final ProspectRepository prospects;
    private final StageHistoryRepository history;
    private final ProposalRevisionRepository proposals;
    private final ProspectService prospectService;
    private final EffectiveStageResolver stages;
    private final MarketingScope scope;
    private final ChangeRecorder changes;
    private final Clock clock;

    public PipelineService(
            ProspectRepository prospects,
            StageHistoryRepository history,
            ProposalRevisionRepository proposals,
            ProspectService prospectService,
            EffectiveStageResolver stages,
            MarketingScope scope,
            ChangeRecorder changes,
            Clock clock) {
        this.prospects = prospects;
        this.history = history;
        this.proposals = proposals;
        this.prospectService = prospectService;
        this.stages = stages;
        this.scope = scope;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public ProspectService.ProspectDto move(UUID actor, Set<Role> roles, UUID id, StageRequest request) {
        Prospect prospect = prospectService.visible(actor, roles, id);
        if (request.version() != null) {
            StaleVersion.check(Prospect.class, id, prospect.getVersion(), request.version());
        }
        if (prospect.isWon()) {
            throw new ConflictException("This prospect has been won; its stage now follows its contract.");
        }
        String wanted = request.stage() == null ? "" : request.stage().trim().toUpperCase(Locale.ROOT);
        String reason = Texts.clean(request.reason(), 300, "Reason");
        ProspectStage before = prospect.getStage();
        switch (wanted) {
            case "RESUME" -> {
                if (before != ProspectStage.ON_HOLD) {
                    throw new ConflictException("Only a prospect on hold can be resumed.");
                }
                prospect.returnToPrevious();
            }
            case "REOPEN" -> {
                if (before != ProspectStage.LOST) {
                    throw new ConflictException("Only a lost prospect can be reopened.");
                }
                prospect.returnToPrevious();
            }
            case "ON_HOLD" -> {
                if (!before.isActive()) {
                    throw new ConflictException("Only a prospect in an active stage can be put on hold.");
                }
                prospect.hold();
            }
            case "LOST" -> {
                if (before == ProspectStage.LOST) {
                    throw new ConflictException("The prospect is already lost.");
                }
                if (reason == null) {
                    throw new InvalidInputException("A reason is required to mark a prospect lost.");
                }
                prospect.lose(reason);
            }
            default -> {
                ProspectStage next = activeStage(wanted);
                if (!before.isActive()) {
                    throw new ConflictException("Resume or reopen the prospect before moving it.");
                }
                if (next == before) {
                    throw new ConflictException("The prospect is already in this stage.");
                }
                if (next == ProspectStage.FINAL_STAGE && !proposals.existsByProspectId(id)) {
                    throw new ConflictException("Record a proposal before moving the prospect to Final Stage.");
                }
                prospect.moveTo(next);
            }
        }
        prospects.saveAndFlush(prospect);
        history.save(new StageHistory(id, StageHistory.STAGE, before, prospect.getStage(), reason, actor, clock.instant()));
        changes.record(actor, "PROSPECT", id, "stage", before, prospect.getStage());
        return prospectService.dtoOf(prospect);
    }

    /** Director or the Zone Manager of the prospect's Zone approves or rejects a Final Stage prospect. */
    @Transactional
    public ProspectService.ProspectDto review(UUID actor, Set<Role> roles, UUID id, ReviewRequest request) {
        Prospect prospect = prospectService.visible(actor, roles, id);
        if (prospect.isWon()) {
            throw new ConflictException("This prospect has already been approved.");
        }
        if (prospect.getStage() != ProspectStage.FINAL_STAGE) {
            throw new ConflictException("Only a prospect in Final Stage can be reviewed.");
        }
        String decision = request.decision() == null ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        String reason = Texts.clean(request.reason(), 300, "Reason");
        switch (decision) {
            case "APPROVE" -> {
                prospect.win(actor, clock.instant());
                prospects.saveAndFlush(prospect);
                history.save(new StageHistory(
                        id, StageHistory.REVIEW_APPROVED, ProspectStage.FINAL_STAGE, ProspectStage.FINAL_STAGE, reason, actor, clock.instant()));
                changes.record(actor, "PROSPECT", id, "review", "FINAL_STAGE", "APPROVED");
            }
            case "REJECT" -> {
                if (reason == null) {
                    throw new InvalidInputException("A reason is required to reject a Final Stage review.");
                }
                prospect.moveTo(ProspectStage.NEGOTIATION);
                prospects.saveAndFlush(prospect);
                history.save(new StageHistory(
                        id, StageHistory.REVIEW_REJECTED, ProspectStage.FINAL_STAGE, ProspectStage.NEGOTIATION, reason, actor, clock.instant()));
                changes.record(actor, "PROSPECT", id, "review", "FINAL_STAGE", "REJECTED: " + reason);
            }
            default -> throw new InvalidInputException("The decision must be APPROVE or REJECT.");
        }
        return prospectService.dtoOf(prospect);
    }

    /** The board: prospects grouped by the stage they show (stored stage, or WON, MOU or ACTIVE once won). */
    @Transactional(readOnly = true)
    public Board board(UUID userId, Set<Role> roles, UUID zone, UUID owner) {
        var view = scope.of(userId, roles);
        List<Prospect> found = prospects.findAll().stream()
                .filter(p -> scope.allowsZone(view, p.getZoneId()))
                .filter(p -> zone == null || p.getZoneId().equals(zone))
                .filter(p -> owner == null || p.getOwnerUserId().equals(owner))
                .toList();
        Map<UUID, String> effective = stages.of(found);
        List<ProspectService.ProspectRow> rows = prospectService.rowsOf(found, effective);
        Map<String, List<ProspectService.ProspectRow>> columns = new LinkedHashMap<>();
        COLUMNS.forEach(c -> columns.put(c, new ArrayList<>()));
        for (ProspectService.ProspectRow row : rows) {
            columns.computeIfAbsent(row.effectiveStage(), k -> new ArrayList<>()).add(row);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        columns.forEach((k, v) -> counts.put(k, v.size()));
        return new Board(columns, counts);
    }

    private static ProspectStage activeStage(String value) {
        try {
            ProspectStage stage = ProspectStage.valueOf(value);
            if (stage.isActive()) {
                return stage;
            }
        } catch (IllegalArgumentException e) {
            // fall through to the message below
        }
        throw new InvalidInputException(
                "The stage must be one of PROSPECT, CONTACTED, VISIT, FOLLOW_UP, INTERESTED, NEGOTIATION, FINAL_STAGE, ON_HOLD, LOST, RESUME or REOPEN.");
    }
}
