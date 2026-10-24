package com.hls.recruitment.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** The three Recruitment tiles (spec 028 US3), read from {@link RecruitmentDashboardService} so they equal the Recruitment dashboard. */
@Component
class RecruitmentTiles implements TileSource {

    private static final String MODULE = "RECRUITMENT";

    private final RecruitmentDashboardService dashboard;

    RecruitmentTiles(RecruitmentDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    private static TileDefinition def(String key, int order, String label, PeriodKind period, Link link) {
        return new TileDefinition(
                key, Area.RECRUITMENT, order, label, period, MODULE, PermissionAction.VIEW, ScopeKind.ALL, false, link);
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                def("recruitment.funnel", 1, "Recruitment funnel", PeriodKind.MONTH, Link.to("/recruitment/dashboard")),
                def("recruitment.joiningRatio", 2, "Joining ratio", PeriodKind.MONTH, Link.to("/recruitment/dashboard")),
                def("recruitment.readyToDeploy", 3, "Ready to deploy", PeriodKind.ALL_TIME,
                        Link.to("/recruitment/induction", "view", "readyToDeploy")));
    }

    private RecruitmentDashboardService.DashboardDto month(TileContext ctx) {
        return ctx.memo("recruitment.month", () ->
                dashboard.dashboard(ctx.userId(), null, ctx.month().atDay(1), ctx.month().atEndOfMonth(), false));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        Link link = tile.link();
        switch (tile.key()) {
            case "recruitment.funnel": {
                RecruitmentDashboardService.Counts c = month(ctx).totals();
                return TileResult.ok(tile.key(),
                        Value.count("Drives held", c.drivesHeld(), link),
                        Value.count("Interviewed", c.interviewed(), link),
                        Value.count("Offers issued", c.offered(), link),
                        Value.count("Offers accepted", c.accepted(), link),
                        Value.count("Joined", c.accepted(), link));
            }
            case "recruitment.joiningRatio": {
                String ratio = month(ctx).totals().joiningRatio();
                return ratio == null
                        ? TileResult.noData(tile.key(), "No selected candidates yet")
                        : TileResult.ok(tile.key(), Value.percent("Joining ratio", new BigDecimal(ratio).doubleValue() * 100, link));
            }
            case "recruitment.readyToDeploy": {
                long ready = ctx.memo("recruitment.all", () ->
                        dashboard.dashboard(ctx.userId(), null, null, null, false)).readyToDeployTotal();
                return TileResult.ok(tile.key(), Value.count("Ready to deploy", ready, link));
            }
            default:
                throw new IllegalArgumentException("Unknown tile " + tile.key());
        }
    }
}
