package com.hls.recruitment.internal;

import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.api.DemandFigures;
import com.hls.recruitment.api.InductionProgress;
import com.hls.recruitment.api.RecruitmentFigures;
import com.hls.recruitment.api.RecruitmentFigures.Counts;
import com.hls.recruitment.api.SupplySource;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportSource;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Recruitment report (spec 014c US4): the funnel per college and in total for a season, from {@link RecruitmentFigures}
 * (the figures behind the 016 dashboard, so they are equal), and the demand against supply of Teachers. The season replaces
 * the month and there is no month state. Anyone who may open the Recruitment dashboard sees the same counts; demand and
 * shortfall (marketing figures) are shown to org-wide callers only. A missing induction or supply provider shows "not
 * available" for those figures only, and a joining ratio with nobody selected shows "no data yet".
 */
@Component
class RecruitmentReportSource implements ReportSource {

    static final String KEY = "recruitment";
    private static final Logger LOG = LoggerFactory.getLogger(RecruitmentReportSource.class);

    private static final List<Column> COLLEGE_COLUMNS = List.of(
            new Column("city", "City", Unit.TEXT),
            new Column("drivesHeld", "Drives held", Unit.COUNT),
            new Column("candidates", "Candidates", Unit.COUNT),
            new Column("assessed", "Interviewed and assessed", Unit.COUNT),
            new Column("selected", "Selected", Unit.COUNT),
            new Column("offered", "Offers issued", Unit.COUNT),
            new Column("joined", "Joined", Unit.COUNT),
            new Column("ratio", "Joining ratio", Unit.TEXT),
            new Column("inducted", "Induction completed", Unit.COUNT),
            new Column("ready", "Ready to deploy", Unit.COUNT));

    private static final List<Column> TOTAL_COLUMNS = List.of(new Column("value", "Teachers", Unit.COUNT));

    private final RecruitmentFigures figures;
    private final DemandFigures demand;
    private final ObjectProvider<InductionProgress> induction;
    private final ObjectProvider<SupplySource> supply;
    private final TransactionTemplate isolated;

    RecruitmentReportSource(
            RecruitmentFigures figures,
            DemandFigures demand,
            ObjectProvider<InductionProgress> induction,
            ObjectProvider<SupplySource> supply,
            PlatformTransactionManager transactionManager) {
        this.figures = figures;
        this.demand = demand;
        this.induction = induction;
        this.supply = supply;
        // demand and supply run in their own read-only transaction, so one that fails cannot poison the section
        this.isolated = new TransactionTemplate(transactionManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolated.setReadOnly(true);
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(KEY, "Recruitment", PermissionModule.REPORT_RECRUITMENT, Set.of(ReportFilter.SEASON), false, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("colleges", "Colleges", COLLEGE_COLUMNS, this::colleges),
                new Part("totals", "Totals, demand and supply", TOTAL_COLUMNS, this::totals));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    private RecruitmentFigures.Result result(ReportContext ctx) {
        return ctx.memo("recruitment.figures", () -> figures.bySeason(ctx.request().season()));
    }

    // ---- Colleges ---------------------------------------------------------------------------------------------------

    private Section colleges(ReportContext ctx) {
        RecruitmentFigures.Result result = result(ctx);
        boolean inductionKnown = induction.getIfAvailable() != null;
        List<Row> rows = new ArrayList<>();
        for (RecruitmentFigures.CollegeRow c : result.colleges()) {
            rows.add(row(c.collegeId().toString(), c.name(), c.city(), c.counts(), inductionKnown));
        }
        Row totals = result.colleges().isEmpty() ? null : row("total", "All colleges", "", result.totals(), inductionKnown);
        return Section.of("colleges", "Colleges", COLLEGE_COLUMNS, rows, totals);
    }

    private static Row row(String id, String label, String city, Counts c, boolean inductionKnown) {
        return new Row(id, label, List.of(
                Cell.of(city == null ? "" : city),
                Cell.of(c.drivesHeld()),
                Cell.of(c.interviewed()),
                Cell.of(c.assessed()),
                Cell.of(c.selected()),
                Cell.of(c.offered()),
                Cell.of(c.accepted()),
                c.joiningRatio() == null ? Cell.noData() : Cell.of(c.joiningRatio()),
                inductionKnown ? Cell.of(c.inducted()) : Cell.notAvailable(),
                inductionKnown ? Cell.of(c.readyToDeploy()) : Cell.notAvailable()));
    }

    // ---- Totals, demand and supply ------------------------------------------------------------------------------------

    private Section totals(ReportContext ctx) {
        List<Row> rows = new ArrayList<>();
        RecruitmentFigures.Result result = result(ctx);
        boolean inductionKnown = induction.getIfAvailable() != null;
        rows.add(new Row("inducted", "Induction completed", List.of(inductionKnown ? Cell.of(result.totals().inducted()) : Cell.notAvailable())));
        rows.add(new Row("ready", "Ready to deploy", List.of(inductionKnown ? Cell.of(result.readyToDeployTotal()) : Cell.notAvailable())));
        if (ctx.scope().orgWide()) {
            Cell demandCell;
            try {
                demandCell = Cell.of(isolated.execute(status -> demand.vacantPositionsOfWonSchools()));
            } catch (RuntimeException e) {
                LOG.warn("Demand could not be read", e);
                demandCell = Cell.notAvailable();
            }
            Cell supplyCell = Cell.notAvailable();
            SupplySource source = supply.getIfAvailable();
            if (source != null) {
                try {
                    OptionalInt ready = isolated.execute(status -> source.readyToDeployCount());
                    supplyCell = ready.isPresent() ? Cell.of((long) ready.getAsInt()) : Cell.notAvailable();
                } catch (RuntimeException e) {
                    LOG.warn("Supply could not be read", e);
                }
            }
            Cell shortfall = demandCell.state() == Cell.State.VALUE && supplyCell.state() == Cell.State.VALUE
                    ? Cell.of(Math.max(0L, ((Number) demandCell.value()).longValue() - ((Number) supplyCell.value()).longValue()))
                    : Cell.notAvailable();
            rows.add(new Row("demand", "Demand (vacant positions of won Schools)", List.of(demandCell)));
            rows.add(new Row("supply", "Supply (recruits ready to deploy)", List.of(supplyCell)));
            rows.add(new Row("shortfall", "Shortfall (demand less supply)", List.of(shortfall)));
        }
        return Section.of("totals", "Totals, demand and supply", TOTAL_COLUMNS, rows, null);
    }

    @Override
    public List<String> notes(ReportContext ctx, com.hls.reporting.api.MonthState state) {
        String season = ctx.request().season();
        return List.of(season == null ? "All seasons." : "Season " + season + ".");
    }
}
