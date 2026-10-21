package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T009: the amendment leaves plannedBetween alone (spec 032 Field day depends on it). */
class MarketingPlannedBetweenUnchangedTest extends MarketingTestBase {

    @Autowired
    MarketingActivities feed;

    @Test
    void plannedBetweenStillReturnsPlannedVisitsOnlyWithTheSameValues() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID planned = activity(admin, prospect, today.plusDays(2));
        UUID completed = activity(admin, prospect, today.plusDays(2));
        UUID cancelled = activity(admin, prospect, today.plusDays(2));
        assertThat(complete(admin, completed, "Done", null).status()).isEqualTo(200);
        assertThat(post(M + "/activities/" + cancelled + "/cancel", admin, Map.of("reason", "Closed")).status()).isEqualTo(200);

        List<PlannedActivity> found = feed.plannedBetween(today, today.plusDays(5), null);

        assertThat(found).extracting(PlannedActivity::id).contains(planned).doesNotContain(completed, cancelled);
        PlannedActivity p = found.stream().filter(a -> a.id().equals(planned)).findFirst().orElseThrow();
        assertThat(p.kind()).isEqualTo("SCHOOL_VISIT");
        assertThat(p.date()).isEqualTo(today.plusDays(2));
        assertThat(p.status()).isEqualTo("PLANNED");
        assertThat(p.place()).isNotBlank();
        assertThat(p.ownerUserId()).isNotNull();
    }
}
