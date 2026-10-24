package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 035 T030 (SC-008): the calendar has no write endpoint and reading it changes no stored row. */
class CalendarReadOnlyTest extends ActivityCalendarTestBase {

    private static final List<String> SOURCE_TABLES = List.of(
            "marketing_activity", "activity_date_history", "campus_drive", "campus_drive_date", "drive_date_change", "task",
            "task_history", "change_history_entry");

    @Test
    void postPutPatchAndDeleteAreRefusedOnTheBasePathAndItsSubPaths() {
        String admin = admin();
        for (String path : new String[] {CAL, CAL + "/", CAL + "/needs-attention", CAL + "/filters", CAL + "/anything"}) {
            for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
                Resp resp = send(method, path, admin, Map.of());
                assertThat(resp.status()).as(method + " " + path).isIn(404, 405);
            }
        }
        for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            assertThat(send(method, CAL, admin, Map.of()).status()).as(method.name()).isEqualTo(405);
        }
    }

    @Test
    void readingTheCalendarChangesNoSourceRow() {
        String admin = admin();
        var before = snapshot();

        assertThat(calendar(admin, "2019-03").status()).isEqualTo(200);
        assertThat(calendar(admin, java.time.YearMonth.now().toString()).status()).isEqualTo(200);
        assertThat(get(CAL, admin).status()).isEqualTo(200);

        assertThat(snapshot()).isEqualTo(before);
    }

    private Map<String, Long> snapshot() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (String table : SOURCE_TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }
}
