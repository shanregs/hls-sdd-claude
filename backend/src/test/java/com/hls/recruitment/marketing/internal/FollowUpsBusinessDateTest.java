package com.hls.recruitment.marketing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: a follow-up due on the 7th is overdue from 00:00 IST on the 8th, not from 05:30 IST. */
class FollowUpsBusinessDateTest {

    @Test
    void followUpDueYesterdayInIstIsOverdueJustAfterIstMidnight() {
        // 2026-11-07T19:00Z is 2026-11-08 00:30 IST.
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        UUID prospect = UUID.randomUUID();
        MarketingActivity visit = mock(MarketingActivity.class);
        when(visit.getId()).thenReturn(UUID.randomUUID());
        when(visit.getProspectId()).thenReturn(prospect);
        when(visit.getStatus()).thenReturn(ActivityStatus.COMPLETED);
        when(visit.getFollowUpOn()).thenReturn(LocalDate.parse("2026-11-07"));
        when(visit.getActivityDate()).thenReturn(LocalDate.parse("2026-11-01"));
        MarketingActivityRepository activities = mock(MarketingActivityRepository.class);
        when(activities.findByProspectIdIn(List.of(prospect))).thenReturn(List.of(visit));

        FollowUps followUps = new FollowUps(activities, new BusinessDate(clock, "Asia/Kolkata"));

        assertThat(followUps.overdueProspects(List.of(prospect))).containsExactly(prospect);
    }
}
