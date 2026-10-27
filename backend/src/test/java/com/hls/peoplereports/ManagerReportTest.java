package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 014c US1: the Manager productivity report equals the dashboard comparison table on the shared columns, shows "no data
 * yet" instead of a false zero, scopes a Zone Manager to their own row and labels an open month.
 */
class ManagerReportTest extends ExpensesTestBase {

    private static final String REPORT = "/api/v1/reports/manager";
    private static final String TABLE = "/api/v1/dashboard/managers";

    private YearMonth month() {
        return YearMonth.from(today().minusDays(1));
    }

    private UUID newTask(String adminToken, UUID assignee) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SCHOOL_VISIT");
        body.put("title", "Visit " + uniqueName("School"));
        body.put("assigneeUserId", assignee);
        body.put("dueOn", today().plusDays(2).toString());
        Resp resp = post("/api/v1/tasks", adminToken, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) ((Map<?, ?>) resp.map().get("task")).get("id"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> reportRowsByName(Resp resp) {
        Map<String, Object> section = ((List<Map<String, Object>>) resp.map().get("sections")).get(0);
        List<Map<String, Object>> columns = (List<Map<String, Object>>) section.get("columns");
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) section.get("rows")) {
            Map<String, Object> byLabel = new LinkedHashMap<>();
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
            for (int i = 0; i < columns.size(); i++) {
                byLabel.put((String) columns.get(i).get("label"), cells.get(i));
            }
            result.put((String) row.get("label"), byLabel);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> tableRowsByName(Resp resp) {
        Map<String, Object> table = (Map<String, Object>) resp.map().get("table");
        List<String> columns = (List<String>) table.get("columns");
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (List<Map<String, Object>> row : (List<List<Map<String, Object>>>) table.get("rows")) {
            Map<String, Object> byLabel = new LinkedHashMap<>();
            for (int i = 1; i < columns.size(); i++) {
                byLabel.put(columns.get(i), row.get(i));
            }
            result.put((String) row.get(0).get("value"), byLabel);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cellOf(Map<String, Object> row, String label) {
        return (Map<String, Object>) row.get(label);
    }

    private String displayNameOf(ManagerCtx m) {
        return jdbc.queryForObject("select display_name from app_user where id = ?", String.class, m.signed().userId());
    }

    private static Object valueOf(Map<String, Object> cell) {
        return cell.get("value");
    }

    @Test
    void commonColumnsEqualTheDashboardComparisonTableAndMissingActivityIsNoDataNotZero() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx one = newManager(admin, zone);
        ManagerCtx two = newManager(admin, zone);
        ManagerCtx quiet = newManager(admin, zone);
        UUID late = newTask(admin, one.signed().userId());
        jdbc.update("update task set due_on = ? where id = ?", today().minusDays(2), late);
        newTask(admin, one.signed().userId());
        newTask(admin, two.signed().userId());
        String period = month().toString();

        Resp report = get(REPORT + "?month=" + period + "&zoneId=" + zone, admin);
        Resp table = get(TABLE + "?zoneId=" + zone + "&period=" + period, admin);

        assertThat(report.status()).as(report.body()).isEqualTo(200);
        assertThat(table.status()).as(table.body()).isEqualTo(200);
        Map<String, Map<String, Object>> shown = reportRowsByName(report);
        Map<String, Map<String, Object>> compared = tableRowsByName(table);
        assertThat(shown).hasSize(3);
        int checked = 0;
        for (var entry : shown.entrySet()) {
            Map<String, Object> reportRow = entry.getValue();
            Map<String, Object> tableRow = compared.get(entry.getKey());
            assertThat(tableRow).as(entry.getKey()).isNotNull();
            for (var column : tableRow.entrySet()) {
                Object dashboard = valueOf(cellOf(tableRow, column.getKey()));
                Map<String, Object> ours = cellOf(reportRow, column.getKey());
                assertThat(ours).as("column " + column.getKey()).isNotNull();
                if ("VALUE".equals(ours.get("state")) && dashboard instanceof Number) {
                    assertThat(((Number) ours.get("value")).longValue()).as(column.getKey()).isEqualTo(((Number) dashboard).longValue());
                    checked++;
                }
            }
            if (entry.getKey().equals(displayNameOf(quiet))) {
                // no planned activity this month: "no data yet", not 0
                assertThat(cellOf(reportRow, "Activities planned").get("state")).isEqualTo("NO_DATA");
                assertThat(cellOf(reportRow, "Activities done").get("state")).isEqualTo("NO_DATA");
            } else {
                // a task due this month is a planned activity
                assertThat(cellOf(reportRow, "Activities planned").get("state")).isEqualTo("VALUE");
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(10);
        Map<String, Object> first = shown.values().stream()
                .filter(r -> ((Number) cellOf(r, "Overdue").get("value")).longValue() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(((Number) cellOf(first, "Tasks assigned").get("value")).longValue()).isEqualTo(2);
    }

    @Test
    void aZoneManagerSeesOnlyTheirOwnRowWithTheirOwnExpenseAndCannotFilterOutsideScope() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID otherZone = zone(admin);
        ManagerCtx mine = newManager(admin, zone);
        ManagerCtx other = newManager(admin, otherZone);
        newTask(admin, mine.signed().userId());
        newTask(admin, other.signed().userId());
        newSubmittedClaim(mine.token(), "55.00");
        newSubmittedClaim(other.token(), "9.00");
        String period = month().toString();

        Resp resp = get(REPORT + "?month=" + period, mine.token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Map<String, Object>> rows = reportRowsByName(resp);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.values().iterator().next();
        assertThat(new java.math.BigDecimal(valueOf(cellOf(row, "Expense claimed")).toString())).isEqualByComparingTo("55.00");
        assertThat(resp.body()).doesNotContain(other.signed().userId().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) ((Map<String, Object>) ((List<?>) resp.map().get("sections")).get(0)).get("totals");
        assertThat(totals).isNotNull();
        assertThat(get(REPORT + "?month=" + period + "&managerId=" + other.managerId(), mine.token()).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + period + "&zoneId=" + otherZone, mine.token()).status()).isEqualTo(403);
        // Admin sees both and the expense of each
        Resp all = get(REPORT + "?month=" + period, admin);
        assertThat(reportRowsByName(all).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void anOpenMonthIsLabelledAndTeacherAndSystemAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        newManager(admin, zone(admin));

        Resp resp = get(REPORT + "?month=" + month(), admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).contains("\"monthState\":\"OPEN\"").contains("still open");
        assertThat(get(REPORT, signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(REPORT, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/reports", signInAs(Role.TEACHER).token()).body()).doesNotContain("\"manager\"");
    }
}
