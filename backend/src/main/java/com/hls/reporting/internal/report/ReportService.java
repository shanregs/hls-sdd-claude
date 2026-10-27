package com.hls.reporting.internal.report;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportOutOfScopeException;
import com.hls.reporting.api.ReportPage;
import com.hls.reporting.api.ReportRequest;
import com.hls.reporting.api.ReportScope;
import com.hls.reporting.api.ReportSource;
import com.hls.reporting.api.MonthState;
import com.hls.reporting.internal.DashboardNotFoundException;
import com.hls.reporting.internal.ZoneResolver;
import com.hls.school.api.SchoolDirectory;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads a report for a caller (spec 014a): checks the grant, resolves the scope and filters, reads each section of the
 * report in its own read-only transaction and within a time limit (so a failing section degrades only itself, FR-004),
 * and makes the CSV from the same page so the file equals the screen. Stores nothing.
 */
@Service
public class ReportService implements DisposableBean {

    static final String ERROR_NOTE = "Could not be read";
    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);

    public record MenuItem(String key, String title, Set<ReportFilter> filters, boolean hasMonthState, boolean canExport, String defaultMonth) {}

    public record Named(UUID id, String name) {}

    public record Menu(List<MenuItem> reports, List<Named> zones, List<Named> schools) {}

    public record CsvFile(String fileName, byte[] bytes) {}

    private final ReportRegistry registry;
    private final ReportScopeResolver scopes;
    private final ZoneResolver zones;
    private final SchoolDirectory schools;
    private final PermissionGuard guard;
    private final ReportExportAudit audit;
    private final Clock clock;
    private final TransactionTemplate readOnly;
    private final long limitMillis;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ReportService(
            ReportRegistry registry,
            ReportScopeResolver scopes,
            ZoneResolver zones,
            SchoolDirectory schools,
            PermissionGuard guard,
            ReportExportAudit audit,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${hls.reports.section-limit-ms:10000}") long limitMillis) {
        this.registry = registry;
        this.scopes = scopes;
        this.zones = zones;
        this.schools = schools;
        this.guard = guard;
        this.audit = audit;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.limitMillis = limitMillis;
    }

    /** The reports the caller may open, with the Zones and Schools they may filter by. Figures are not read here. */
    @Transactional(readOnly = true)
    public Menu menu(UUID userId, Set<Role> roles) {
        List<ReportSource> sources = registry.menuFor(roles);
        ReportScope scope = scopes.scopeOf(userId, roles);
        YearMonth current = YearMonth.from(zones.today());
        List<MenuItem> items = new ArrayList<>();
        for (ReportSource source : sources) {
            ReportDefinition d = source.definition();
            YearMonth month = DefaultMonth.choose(lastFinal(source, userId, roles, scope), current);
            items.add(new MenuItem(
                    d.key(), d.title(), d.filters(), d.hasMonthState(),
                    d.exportAllowed() && guard.isGranted(roles, d.module(), PermissionAction.EXPORT), month.toString()));
        }
        boolean wantsFilters = items.stream().anyMatch(i -> i.filters().contains(ReportFilter.ZONE) || i.filters().contains(ReportFilter.SCHOOL));
        if (!wantsFilters || scope.ownOnly()) {
            return new Menu(items, List.of(), List.of());
        }
        List<Named> zoneOptions =
                zones.pickableZones(userId, roles).stream().map(z -> new Named(z.id(), z.name())).toList();
        List<SchoolDirectory.SchoolInfo> all = schools.allSchools();
        List<Named> schoolOptions = all.stream()
                .filter(SchoolDirectory.SchoolInfo::active)
                .filter(s -> scope.allowsSchool(s.id(), s.zoneId()))
                .map(s -> new Named(s.id(), s.name()))
                .sorted(java.util.Comparator.comparing(Named::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Menu(items, zoneOptions, schoolOptions);
    }

    public ReportPage read(
            UUID userId, Set<Role> roles, String key, String month, String zone, String school, String teacher, String manager) {
        Prepared p = prepare(userId, roles, key, PermissionAction.VIEW, month, zone, school, teacher, manager);
        return readPage(p);
    }

    /** The CSV of the same page the screen shows, and one audit row. Needs the Export grant. */
    @Transactional
    public CsvFile export(
            UUID userId, Set<Role> roles, String key, String month, String zone, String school, String teacher, String manager) {
        Prepared p = prepare(userId, roles, key, PermissionAction.EXPORT, month, zone, school, teacher, manager);
        if (!p.source().definition().exportAllowed()) {
            throw new DashboardNotFoundException("Report not found.");
        }
        ReportPage page = readPage(p);
        String filterText = filterText(p.request());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
        ReportCsvWriter.write(writer, page, filterText, Instant.now(clock));
        int rows = page.sections().stream().mapToInt(s -> s.rows().size()).sum();
        audit.exported(userId, key, p.request(), filterText, rows);
        return new CsvFile(key + "-report-" + page.month() + ".csv", bytes.toByteArray());
    }

    private record Prepared(ReportSource source, ReportRequestContext ctx, ReportRequest request) {}

    private Prepared prepare(
            UUID userId, Set<Role> roles, String key, PermissionAction action, String month, String zone, String school,
            String teacher, String manager) {
        ReportSource source = registry.find(key).orElseThrow(() -> new DashboardNotFoundException("Report not found."));
        ReportDefinition d = source.definition();
        guard.require(roles, d.module(), action);
        ReportScope scope = scopes.scopeOf(userId, roles);
        YearMonth chosen = month == null || month.isBlank()
                ? DefaultMonth.choose(lastFinal(source, userId, roles, scope), YearMonth.from(zones.today()))
                : ReportScopeResolver.month(month);
        ReportRequest request = scopes.request(scope, d.filters(), chosen, zone, school, teacher, manager);
        return new Prepared(source, new ReportRequestContext(userId, roles, zones.today(), scope, request), request);
    }

    private java.util.Optional<YearMonth> lastFinal(ReportSource source, UUID userId, Set<Role> roles, ReportScope scope) {
        try {
            return source.lastFinalMonth(userId, roles, scope);
        } catch (RuntimeException e) {
            LOG.warn("Report {} could not find its last final month", source.definition().key(), e);
            return java.util.Optional.empty();
        }
    }

    private ReportPage readPage(Prepared p) {
        ReportSource source = p.source();
        ReportContext ctx = p.ctx();
        long startedAt = System.nanoTime();
        List<ReportSource.SectionSource> sections = source.sections();
        List<Future<ReportPage.Section>> futures = new ArrayList<>();
        for (ReportSource.SectionSource section : sections) {
            futures.add(executor.submit(() -> readOnly.execute(status -> section.read(ctx))));
        }
        List<ReportPage.Section> done = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            done.add(await(futures.get(i), sections.get(i), startedAt));
        }
        MonthState state = safeState(source, ctx);
        List<String> notes;
        try {
            notes = source.notes(ctx, state);
        } catch (RuntimeException e) {
            notes = List.of();
        }
        return new ReportPage(
                source.definition().key(), source.definition().title(), ctx.month().toString(), state, done, notes);
    }

    private MonthState safeState(ReportSource source, ReportContext ctx) {
        try {
            MonthState state = source.monthState(ctx);
            return state == null ? MonthState.NONE : state;
        } catch (RuntimeException e) {
            LOG.warn("Report {} could not read its month state", source.definition().key(), e);
            return MonthState.NONE;
        }
    }

    private ReportPage.Section await(Future<ReportPage.Section> future, ReportSource.SectionSource section, long startedAt) {
        long remaining = limitMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        try {
            ReportPage.Section result = future.get(Math.max(remaining, 1), TimeUnit.MILLISECONDS);
            if (result == null) {
                return ReportPage.Section.failed(section.key(), section.title(), section.columns(), ERROR_NOTE);
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.warn("Report section {} exceeded {} ms", section.key(), limitMillis);
            return ReportPage.Section.failed(section.key(), section.title(), section.columns(), ERROR_NOTE);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ReportOutOfScopeException outOfScope) {
                throw outOfScope;
            }
            LOG.warn("Report section {} failed", section.key(), e.getCause());
            return ReportPage.Section.failed(section.key(), section.title(), section.columns(), ERROR_NOTE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReportPage.Section.failed(section.key(), section.title(), section.columns(), ERROR_NOTE);
        }
    }

    private static String filterText(ReportRequest r) {
        List<String> parts = new ArrayList<>();
        if (r.zoneId() != null) {
            parts.add("zone=" + r.zoneId());
        }
        if (r.schoolId() != null) {
            parts.add("school=" + r.schoolId());
        }
        if (r.teacherId() != null) {
            parts.add("teacher=" + r.teacherId());
        }
        if (r.managerId() != null) {
            parts.add("manager=" + r.managerId());
        }
        return String.join("; ", parts);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
