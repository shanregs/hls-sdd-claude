package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014c A8: visits done and Schools won in the month for a seeded month; visits missed and Schools won are unchanged. */
class MarketingManagerColumnsTest extends MarketingTestBase {

    @Autowired
    private List<ManagerColumns> sources;

    private ManagerColumns marketing() {
        return sources.stream()
                .filter(s -> s.columns().stream().anyMatch(c -> c.key().equals("marketing.visitsDone")))
                .findFirst()
                .orElseThrow();
    }

    private static TileContext context(YearMonth month) {
        return new TileContext() {
            public UUID userId() {
                return UUID.randomUUID();
            }

            public Set<Role> roles() {
                return Set.of(Role.ADMIN);
            }

            public LocalDate today() {
                return LocalDate.now();
            }

            public YearMonth month() {
                return month;
            }

            public Optional<UUID> zoneId() {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            public <T> T memo(String key, Supplier<T> compute) {
                return compute.get();
            }
        };
    }

    @Test
    void visitsDoneAndWonInMonthAreCountedForTheMonthAndTheOldColumnsKeepTheirMeaning() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx m = newManager(admin, zone);
        UUID userId = m.signed().userId();
        UUID prospect = prospect(admin, zone);
        YearMonth month = YearMonth.of(2020, 3);
        LocalDate far = LocalDate.now().plusDays(30);

        UUID done = activity(m.token(), prospect, far);
        assertThat(complete(m.token(), done, "Met the principal", null).status()).isEqualTo(200);
        jdbc.update("update marketing_activity set activity_date = ? where id = ?", LocalDate.of(2020, 3, 5), done);
        UUID missed = activity(m.token(), prospect, far);
        jdbc.update("update marketing_activity set activity_date = ? where id = ?", LocalDate.of(2020, 3, 9), missed);
        UUID elsewhere = activity(m.token(), prospect, far);
        assertThat(complete(m.token(), elsewhere, "Met the principal", null).status()).isEqualTo(200);
        jdbc.update("update marketing_activity set activity_date = ? where id = ?", LocalDate.of(2020, 4, 5), elsewhere);

        UUID wonThen = prospect(admin, zone);
        UUID wonBefore = prospect(admin, zone);
        jdbc.update("update marketing_prospect set owner_user_id = ?, won_by = ?, won_at = ?::timestamptz where id = ?",
                userId, userId, "2020-03-15T10:00:00+05:30", wonThen);
        jdbc.update("update marketing_prospect set owner_user_id = ?, won_by = ?, won_at = ?::timestamptz where id = ?",
                userId, userId, "2020-01-15T10:00:00+05:30", wonBefore);

        Map<String, Long> march = marketing().figures(context(month), Set.of(userId)).get(userId);
        Map<String, Long> april = marketing().figures(context(month.plusMonths(1)), Set.of(userId)).get(userId);

        assertThat(march).containsEntry("marketing.visitsDone", 1L).containsEntry("marketing.visitsMissed", 1L)
                .containsEntry("marketing.wonInMonth", 1L).containsEntry("marketing.won", 2L);
        assertThat(april).containsEntry("marketing.visitsDone", 1L).containsEntry("marketing.visitsMissed", 0L)
                .containsEntry("marketing.wonInMonth", 0L).containsEntry("marketing.won", 2L);
    }
}
