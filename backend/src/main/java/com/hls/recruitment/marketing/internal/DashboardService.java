package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeView;
import com.hls.recruitment.api.SupplySource;
import com.hls.school.api.SchoolDirectory;
import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.SchoolContracts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The marketing dashboard: visits, prospects by stage, win rate, Schools won per Zone and owner, and demand (vacant
 * positions of won Schools with a live contract) against supply (recruits ready to deploy, "not available" when no
 * module provides it). A Zone Manager's numbers cover only their Zones.
 */
@Service
public class DashboardService {

    public record Visits(long planned, long completed, long missed, long cancelled) {}

    public record WonBy(String name, long won) {}

    public record DashboardDto(
            String period,
            Visits visits,
            Map<String, Long> prospectsByStage,
            long won,
            long lost,
            String winRate,
            List<WonBy> wonPerZone,
            List<WonBy> wonPerOwner,
            long demand,
            Integer supply,
            Integer shortfall) {}

    private final ProspectRepository prospects;
    private final MarketingActivityRepository activities;
    private final EffectiveStageResolver stages;
    private final ProspectService prospectService;
    private final SchoolDirectory schools;
    private final MarketingScope scope;
    private final ObjectProvider<SchoolContracts> contracts;
    private final ObjectProvider<SupplySource> supply;
    private final Clock clock;

    public DashboardService(
            ProspectRepository prospects,
            MarketingActivityRepository activities,
            EffectiveStageResolver stages,
            ProspectService prospectService,
            SchoolDirectory schools,
            MarketingScope scope,
            ObjectProvider<SchoolContracts> contracts,
            ObjectProvider<SupplySource> supply,
            Clock clock) {
        this.prospects = prospects;
        this.activities = activities;
        this.stages = stages;
        this.prospectService = prospectService;
        this.schools = schools;
        this.scope = scope;
        this.contracts = contracts;
        this.supply = supply;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(UUID userId, Set<Role> roles, String period) {
        ScopeView view = scope.of(userId, roles);
        YearMonth month = periodOf(period);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = LocalDate.now(clock);

        List<Prospect> visible = prospects.findAll().stream().filter(p -> scope.allowsZone(view, p.getZoneId())).toList();
        Set<UUID> visibleIds = visible.stream().map(Prospect::getId).collect(Collectors.toSet());
        Map<UUID, String> effective = stages.of(visible);

        List<MarketingActivity> inPeriod = activities.findByActivityDateBetweenOrderByActivityDate(from, to).stream()
                .filter(a -> a.getProspectId() == null ? a.getSchoolId() != null && view.allowsSchool(a.getSchoolId()) : visibleIds.contains(a.getProspectId()))
                .toList();
        Visits visits = new Visits(
                inPeriod.stream().filter(a -> a.getStatus() == ActivityStatus.PLANNED && !a.getActivityDate().isBefore(today)).count(),
                inPeriod.stream().filter(a -> a.getStatus() == ActivityStatus.COMPLETED).count(),
                inPeriod.stream().filter(a -> a.getStatus() == ActivityStatus.PLANNED && a.getActivityDate().isBefore(today)).count(),
                inPeriod.stream().filter(a -> a.getStatus() == ActivityStatus.CANCELLED).count());

        Map<String, Long> byStage = new LinkedHashMap<>();
        for (String stage : List.of("PROSPECT", "CONTACTED", "VISIT", "FOLLOW_UP", "INTERESTED", "NEGOTIATION", "FINAL_STAGE", "WON", "MOU", "ACTIVE", "ON_HOLD", "LOST")) {
            byStage.put(stage, 0L);
        }
        effective.values().forEach(s -> byStage.merge(s, 1L, Long::sum));

        long won = visible.stream().filter(Prospect::isWon).count();
        long lost = visible.stream().filter(p -> p.getStage() == ProspectStage.LOST).count();
        String winRate = won + lost == 0
                ? null
                : BigDecimal.valueOf(won).divide(BigDecimal.valueOf(won + lost), 2, RoundingMode.HALF_UP).toPlainString();

        List<Prospect> wonList = visible.stream().filter(Prospect::isWon).toList();
        Map<UUID, String> zoneNames = schools.zones(wonList.stream().map(Prospect::getZoneId).distinct().toList()).stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<UUID, String> ownerNames = prospectService.namesOf(wonList.stream().map(Prospect::getOwnerUserId).collect(Collectors.toSet()));
        List<WonBy> perZone = wonList.stream()
                .collect(Collectors.groupingBy(p -> zoneNames.getOrDefault(p.getZoneId(), "-"), Collectors.counting()))
                .entrySet().stream().map(e -> new WonBy(e.getKey(), e.getValue())).sorted(java.util.Comparator.comparing(WonBy::name)).toList();
        List<WonBy> perOwner = wonList.stream()
                .collect(Collectors.groupingBy(p -> ownerNames.getOrDefault(p.getOwnerUserId(), "Unknown user"), Collectors.counting()))
                .entrySet().stream().map(e -> new WonBy(e.getKey(), e.getValue())).sorted(java.util.Comparator.comparing(WonBy::name)).toList();

        long demand = 0;
        SchoolContracts source = contracts.getIfAvailable();
        Set<UUID> wonSchools = wonList.stream().map(Prospect::getSchoolId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (source != null && !wonSchools.isEmpty()) {
            for (Occupancy o : source.occupancyOfAll(wonSchools, today).values()) {
                demand += o.vacant();
            }
        }
        SupplySource supplySource = supply.getIfAvailable();
        Integer supplied = supplySource == null ? null : supplySource.readyToDeployCount().isPresent() ? supplySource.readyToDeployCount().getAsInt() : null;
        Integer shortfall = supplied == null ? null : (int) Math.max(0, demand - supplied);
        return new DashboardDto(month.toString(), visits, byStage, won, lost, winRate, perZone, perOwner, demand, supplied, shortfall);
    }

    private YearMonth periodOf(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(clock.withZone(ZoneOffset.UTC));
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw new com.hls.school.api.InvalidInputException("The period must look like 2026-10.");
        }
    }
}
