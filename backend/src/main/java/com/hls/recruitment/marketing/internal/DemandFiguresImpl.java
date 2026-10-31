package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.DemandFigures;
import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.SchoolContracts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demand rule of the marketing dashboard (spec 023): vacant positions of won Schools with a live contract. The dashboard
 * and the reports (spec 014c A2) both call {@link #demandOf}, so they cannot disagree.
 */
@Component
class DemandFiguresImpl implements DemandFigures {

    private final ProspectRepository prospects;
    private final ObjectProvider<SchoolContracts> contracts;
    private final Clock clock;

    DemandFiguresImpl(ProspectRepository prospects, ObjectProvider<SchoolContracts> contracts, Clock clock) {
        this.prospects = prospects;
        this.contracts = contracts;
        this.clock = clock;
    }

    /** The vacant positions of the Schools of these won prospects. */
    long demandOf(Collection<Prospect> won) {
        SchoolContracts source = contracts.getIfAvailable();
        Set<UUID> schools = won.stream().map(Prospect::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        long demand = 0;
        if (source != null && !schools.isEmpty()) {
            for (Occupancy o : source.occupancyOfAll(schools, LocalDate.now(clock)).values()) {
                demand += o.vacant();
            }
        }
        return demand;
    }

    @Override
    @Transactional(readOnly = true)
    public long vacantPositionsOfWonSchools() {
        return demandOf(prospects.findAll().stream().filter(Prospect::isWon).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long vacantPositionsOfWonSchools(Collection<UUID> zoneIds) {
        return demandOf(prospects.findAll().stream()
                .filter(Prospect::isWon)
                .filter(p -> p.getZoneId() != null && zoneIds.contains(p.getZoneId()))
                .toList());
    }
}
