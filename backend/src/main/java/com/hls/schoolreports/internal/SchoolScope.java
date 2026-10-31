package com.hls.schoolreports.internal;

import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportScope;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the three School reports share: the Schools the caller may see for the request (scope first, then the Zone and School
 * filters, so a total never includes a School the caller cannot see) and the transaction each optional source is read in. An
 * empty result means an empty report, never a wider one (spec 014b D11).
 */
@Component
class SchoolScope {

    private final SchoolDirectory schools;
    private final TransactionTemplate isolated;

    SchoolScope(SchoolDirectory schools, PlatformTransactionManager transactionManager) {
        this.schools = schools;
        // each optional source runs in its own read-only transaction, so one that fails cannot poison the section (D7)
        this.isolated = new TransactionTemplate(transactionManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolated.setReadOnly(true);
    }

    /** The Schools in the caller's scope that pass the request's Zone and School filters, by name; memoised per request. */
    List<SchoolInfo> inScope(ReportContext ctx) {
        return ctx.memo("schoolreports.schools", () -> compute(ctx));
    }

    /** The ids of every School the scope allows, ignoring the request filters; null for an organization-wide scope. */
    Set<UUID> visibleSchoolIds(ReportScope scope) {
        if (scope.orgWide()) {
            return null;
        }
        return schools.allSchools().stream()
                .filter(s -> scope.allowsSchool(s.id(), s.zoneId()))
                .map(SchoolInfo::id)
                .collect(Collectors.toSet());
    }

    private List<SchoolInfo> compute(ReportContext ctx) {
        ReportScope scope = ctx.scope();
        Set<UUID> zones = scope.zoneIds();
        Set<UUID> ids = scope.schoolIds();
        UUID zoneFilter = ctx.request().zoneId();
        UUID schoolFilter = ctx.request().schoolId();
        return schools.allSchools().stream()
                .filter(s -> scope.orgWide() || ids.contains(s.id()) || (s.zoneId() != null && zones.contains(s.zoneId())))
                .filter(s -> zoneFilter == null || zoneFilter.equals(s.zoneId()))
                .filter(s -> schoolFilter == null || schoolFilter.equals(s.id()))
                .sorted(Comparator.comparing(SchoolInfo::name, String.CASE_INSENSITIVE_ORDER).thenComparing(SchoolInfo::id))
                .toList();
    }

    /** Every School, whatever the caller may see: used to split a Teacher's pay across all the Schools that billed them. */
    List<SchoolInfo> all() {
        return schools.allSchools();
    }

    Map<UUID, String> zoneNames(ReportContext ctx) {
        return ctx.memo("schoolreports.zoneNames", () -> {
            Map<UUID, String> names = new HashMap<>();
            schools.allZones().forEach(z -> names.put(z.id(), z.name()));
            return names;
        });
    }

    Map<UUID, SchoolInfo> byId(List<SchoolInfo> list) {
        return list.stream().collect(Collectors.toMap(SchoolInfo::id, s -> s, (a, b) -> a));
    }

    /** Runs the read in its own read-only transaction. */
    <T> T isolated(java.util.function.Supplier<T> read) {
        return isolated.execute(status -> read.get());
    }
}
