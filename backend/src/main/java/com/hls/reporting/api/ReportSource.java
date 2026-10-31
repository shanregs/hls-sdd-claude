package com.hls.reporting.api;

import com.hls.identity.user.Role;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A report, published by the module that owns its figures (like {@link TileSource}); {@code reporting} finds every
 * {@code ReportSource} bean, checks the grant, resolves the scope and reads the sections. Adding a report means adding a
 * bean, with no change to the framework (spec 014a SC-006).
 */
public interface ReportSource {

    ReportDefinition definition();

    /**
     * The sections of the report. The framework reads each one on its own, within a time limit and in a read-only
     * transaction, so one section failing never fails the page.
     */
    List<SectionSource> sections();

    /** The month state to label the page with; called after the sections are read, so memoised values are shared. */
    default MonthState monthState(ReportContext ctx) {
        return MonthState.NONE;
    }

    /** Notes for the reader, for example that a draft run is not final. */
    default List<String> notes(ReportContext ctx, MonthState state) {
        return List.of();
    }

    /** The most recent month the report treats as final for this caller, when it has one (the default month, FR-005). */
    default Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope scope) {
        return Optional.empty();
    }

    /** One independently readable part of a report. */
    interface SectionSource {

        String key();

        String title();

        List<ReportPage.Column> columns();

        ReportPage.Section read(ReportContext ctx);
    }
}
