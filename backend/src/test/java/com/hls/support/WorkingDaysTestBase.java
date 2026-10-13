package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 037 tests: School working days rows and the year they are tested in. */
public abstract class WorkingDaysTestBase extends AttendanceTestBase {

    protected static final String BASE = "/api/v1/school-working-days";

    /** The current year in the business zone; rows are created inside the allowed window around it. */
    protected int thisYear() {
        return businessCalendar.today().getYear();
    }

    protected Resp createRow(String token, UUID schoolId, int year, int month, Object days) {
        return post(
                BASE,
                token,
                Map.of("schoolId", schoolId, "year", year, "month", month, "workingDays", days));
    }

    protected UUID createRowOk(String token, UUID schoolId, int year, int month, Object days) {
        Resp resp = createRow(token, schoolId, year, month, days);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp editRow(String token, UUID id, Object days, long version) {
        return put(BASE + "/" + id, token, Map.of("workingDays", days, "version", version));
    }

    protected Resp deleteRow(String token, UUID id, long version) {
        return delete(BASE + "/" + id + "?version=" + version, token);
    }

    protected static long versionOf(Resp row) {
        return ((Number) row.map().get("version")).longValue();
    }

    /** The grid rows of one School and year, as the list endpoint returns them. */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> items(Resp page) {
        assertThat(page.status()).as(page.body()).isEqualTo(200);
        return (List<Map<String, Object>>) page.map().get("items");
    }

    protected Resp listRows(String token, int year, UUID schoolId) {
        return get(BASE + "?year=" + year + (schoolId == null ? "" : "&schoolId=" + schoolId) + "&size=100", token);
    }

    protected long historyCount(UUID schoolId) {
        return jdbc.queryForObject(
                "select count(*) from school_working_days_history where school_id = ?", Long.class, schoolId);
    }

    protected long auditCount(UUID rowId) {
        return jdbc.queryForObject(
                "select count(*) from change_history_entry where entity_type = 'SCHOOL_WORKING_DAYS' and entity_id = ?",
                Long.class,
                rowId.toString());
    }

    /** The fewest statements the action needed over three runs; one run may refresh a cached snapshot. */
    protected long fewestStatements(org.hibernate.SessionFactory sessionFactory, Runnable action) {
        var stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        long fewest = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            stats.clear();
            action.run();
            fewest = Math.min(fewest, stats.getPrepareStatementCount());
        }
        stats.setStatisticsEnabled(false);
        return fewest;
    }

    protected static LocalDate firstOf(int year, int month) {
        return LocalDate.of(year, month, 1);
    }
}
