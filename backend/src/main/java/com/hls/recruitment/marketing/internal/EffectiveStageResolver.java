package com.hls.recruitment.marketing.internal;

import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.SchoolContracts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The stage a prospect shows on the board. A prospect that is not won shows its stored stage. A won prospect shows
 * WON until its School has a live contract (MoU) in spec 012, then MOU, then ACTIVE once a position is filled. Nothing
 * about the contract is stored here, so the board can never disagree with it; with spec 012 absent a won prospect stays
 * WON.
 */
@Component
class EffectiveStageResolver {

    static final String WON = "WON";
    static final String MOU = "MOU";
    static final String ACTIVE = "ACTIVE";

    private final ObjectProvider<SchoolContracts> contracts;
    private final Clock clock;

    EffectiveStageResolver(ObjectProvider<SchoolContracts> contracts, Clock clock) {
        this.contracts = contracts;
        this.clock = clock;
    }

    /** One batch call to spec 012 for every won prospect with a School, however many prospects there are. */
    Map<UUID, String> of(List<Prospect> prospects) {
        Map<UUID, String> result = new HashMap<>();
        Set<UUID> schoolIds = prospects.stream()
                .filter(p -> p.isWon() && p.getSchoolId() != null)
                .map(Prospect::getSchoolId)
                .collect(Collectors.toSet());
        Map<UUID, Occupancy> occupancy = Map.of();
        SchoolContracts source = contracts.getIfAvailable();
        if (source != null && !schoolIds.isEmpty()) {
            occupancy = source.occupancyOfAll(schoolIds, LocalDate.now(clock));
        }
        for (Prospect p : prospects) {
            if (!p.isWon()) {
                result.put(p.getId(), p.getStage().name());
                continue;
            }
            Occupancy o = p.getSchoolId() == null ? Occupancy.NONE : occupancy.getOrDefault(p.getSchoolId(), Occupancy.NONE);
            result.put(p.getId(), o.positions() == 0 ? WON : o.filled() > 0 ? ACTIVE : MOU);
        }
        return result;
    }
}
