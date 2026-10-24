package com.hls.recruitment.marketing.internal;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The visit tile of the Managers area and the five Schools tiles (spec 028 US2, US3). Every figure is read from the
 * marketing {@link DashboardService}, memoized per request (and per Zone choice), so a tile can never differ from it.
 */
@Component
class MarketingTiles implements TileSource {

    private static final String MODULE = "MARKETING";
    private static final String VISITS = "managers.visits";

    private final DashboardService dashboard;

    MarketingTiles(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    private static TileDefinition def(String key, Area area, int order, String label, PeriodKind period, boolean zone, Link link) {
        return new TileDefinition(key, area, order, label, period, MODULE, PermissionAction.VIEW, ScopeKind.ALL, zone, link);
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                def(VISITS, Area.MANAGERS, 6, "School visits", PeriodKind.MONTH, true, Link.to("/marketing/calendar")),
                def("schools.stages", Area.SCHOOLS, 1, "Prospects by stage", PeriodKind.ALL_TIME, true, Link.to("/marketing/pipeline")),
                def("schools.finalStage", Area.SCHOOLS, 2, "Final Stage awaiting review", PeriodKind.ALL_TIME, true,
                        Link.to("/marketing/prospects", "stage", "FINAL_STAGE")),
                def("schools.visits", Area.SCHOOLS, 3, "Visits", PeriodKind.MONTH, true, Link.to("/marketing/calendar")),
                def("schools.winLoss", Area.SCHOOLS, 4, "Won and lost", PeriodKind.MONTH, true, Link.to("/marketing/prospects")),
                def("schools.demandSupply", Area.SCHOOLS, 5, "Demand and supply", PeriodKind.ALL_TIME, false, Link.to("/marketing/dashboard")));
    }

    private DashboardService.DashboardDto read(TileContext ctx, boolean zoned) {
        java.util.UUID zone = zoned ? ctx.zoneId().orElse(null) : null;
        return ctx.memo("marketing.dashboard." + zone, () -> dashboard.dashboard(ctx.userId(), ctx.roles(), ctx.month().toString(), zone));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        DashboardService.DashboardDto d = read(ctx, tile.zoneAware());
        Link link = tile.link();
        return switch (tile.key()) {
            case VISITS, "schools.visits" -> TileResult.ok(tile.key(),
                    Value.count("Planned", d.visits().planned(), link),
                    Value.count("Completed", d.visits().completed(), link),
                    Value.count("Missed", d.visits().missed(), link));
            case "schools.stages" -> {
                List<Value> values = new ArrayList<>();
                for (Map.Entry<String, Long> e : d.prospectsByStage().entrySet()) {
                    values.add(Value.count(e.getKey(), e.getValue(), link));
                }
                yield new TileResult(tile.key(), TileResult.State.OK, values, false, null, null, null);
            }
            case "schools.finalStage" -> TileResult.ok(tile.key(),
                    Value.count("Awaiting review", d.prospectsByStage().getOrDefault("FINAL_STAGE", 0L), link));
            case "schools.winLoss" -> {
                List<Value> values = new ArrayList<>(List.of(Value.count("Won", d.won(), link), Value.count("Lost", d.lost(), link)));
                if (d.winRate() == null) {
                    yield TileResult.noData(tile.key(), "No won or lost Schools yet");
                }
                values.add(Value.percent("Win rate", new BigDecimal(d.winRate()).doubleValue() * 100, link));
                yield new TileResult(tile.key(), TileResult.State.OK, values, false, null, null, null);
            }
            case "schools.demandSupply" -> d.supply() == null
                    ? TileResult.notAvailable(tile.key(), "Supply of recruits is not available")
                    : TileResult.ok(tile.key(),
                            Value.count("Demand", d.demand(), link),
                            Value.count("Supply", d.supply(), link),
                            Value.count("Shortfall", d.shortfall(), link));
            default -> throw new IllegalArgumentException("Unknown tile " + tile.key());
        };
    }
}
