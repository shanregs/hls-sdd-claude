package com.hls.incentive.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.incentive.api.IncentiveSummary;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import java.util.List;
import org.springframework.stereotype.Component;

/** The Manager incentives tile of the management Dashboard (spec 030 FR-014, D-12), within the caller's scope. */
@Component
class IncentiveTiles implements TileSource {

    static final String KEY = "incentives.summary";

    private final IncentiveSummary summary;

    IncentiveTiles(IncentiveSummary summary) {
        this.summary = summary;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.PAYROLL, 4, "Manager incentives", PeriodKind.MONTH, "MANAGER_INCENTIVES", PermissionAction.VIEW,
                ScopeKind.ALL, false, Link.to("/operations/incentives")));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        IncentiveSummary.Counts counts = ctx.memo("incentives.counts", () -> summary.counts(ctx.userId(), ctx.roles(), ctx.month()));
        Link pending = Link.to("/operations/incentives", "status", "PENDING_APPROVAL");
        Link approved = Link.to("/operations/incentives", "status", "APPROVED");
        Link paid = Link.to("/operations/incentives", "status", "PAID_IN_RUN");
        return TileResult.ok(
                KEY,
                Value.count("Waiting for a decision", counts.pending(), pending),
                Value.count("Approved, not yet paid", counts.approvedAwaitingPayroll(), approved),
                Value.rupees("Paid this month", counts.paidInMonth(), paid),
                Value.count("Schools with no earner", counts.noEarner(), Link.to("/operations/incentives", "tab", "no-earner")));
    }
}
