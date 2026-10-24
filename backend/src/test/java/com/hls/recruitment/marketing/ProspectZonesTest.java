package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.api.ProspectZones;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T020: the bulk prospect Zone lookup. */
class ProspectZonesTest extends MarketingTestBase {

    @Autowired
    ProspectZones zones;

    @Test
    void returnsTheZoneOfEachKnownProspectAndSkipsUnknownIds() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        UUID a = prospect(admin, zoneA);
        UUID b = prospect(admin, zoneB);
        UUID unknown = UUID.randomUUID();

        var found = zones.zonesOf(List.of(a, b, unknown));

        assertThat(found).containsEntry(a, zoneA).containsEntry(b, zoneB).doesNotContainKey(unknown);
        assertThat(zones.zonesOf(List.of())).isEmpty();
    }
}
