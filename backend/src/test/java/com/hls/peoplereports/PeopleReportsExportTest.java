package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 014c US5: the Recruitment, Teacher, Manager and Expenses reports export through the 014a writer. The file carries what
 * the screen shows, a caller without the Export grant is refused the file, every export writes exactly one audit row, and a
 * name that starts like a formula is neutralised.
 */
class PeopleReportsExportTest extends ExpensesTestBase {

    /** Report key and the title the CSV carries on its first line. */
    private static final Map<String, String> TITLES = Map.of(
            "manager", "Manager productivity",
            "teacher", "Teachers",
            "expenses", "Expenses",
            "recruitment", "Recruitment");

    private static final String BOM = "﻿";

    private String month() {
        return YearMonth.from(today().minusDays(1)).toString();
    }

    private String path(String key) {
        return "/api/v1/reports/" + key;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rowLabels(Resp screen) {
        return ((List<Map<String, Object>>) screen.map().get("sections"))
                .stream()
                .flatMap(s -> ((List<Map<String, Object>>) s.get("rows")).stream())
                .map(r -> String.valueOf(r.get("label")))
                .toList();
    }

    private int exportAuditRows(String admin, String key) {
        Resp resp = get(
                "/api/v1/audit/change-history?entityType=REPORT_EXPORT_" + key.toUpperCase() + "&entityId=" + key + "&size=100",
                admin);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp.body().split("\"field\":\"exported\"", -1).length - 1;
    }

    @Test
    void everyReportExportsWhatItsScreenShowsAndEachExportWritesOneAuditRow() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        newManager(admin, zone);
        newManager(admin, zone);
        String query = "?month=" + month() + "&zoneId=" + zone;

        for (var report : TITLES.entrySet()) {
            String key = report.getKey();
            // Recruitment has no Zone filter, so it is asked without the query a Zone would refuse.
            String asked = "recruitment".equals(key) ? "" : query;
            int before = exportAuditRows(admin, key);

            Resp screen = get(path(key) + asked, admin);
            Resp csv = get(path(key) + "/export" + asked, admin);

            assertThat(screen.status()).as(key + " screen: " + screen.body()).isEqualTo(200);
            assertThat(csv.status()).as(key + " export: " + csv.body()).isEqualTo(200);
            assertThat(csv.body()).as(key).startsWith(BOM + "Report," + report.getValue());
            for (String label : rowLabels(screen)) {
                assertThat(csv.body()).as(key + " row " + label).contains(label);
            }
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(exportAuditRows(admin, key)).as(key).isEqualTo(before + 1));
            assertThat(exportAuditRows(admin, key)).as(key + " after the wait").isEqualTo(before + 1);
        }
    }

    @Test
    void aTeacherGetsNeitherTheScreenNorTheFileOfAnyOfTheFour() {
        String teacher = teacherUser().token();

        for (String key : TITLES.keySet()) {
            assertThat(get(path(key) + "?month=" + month(), teacher).status()).as(key + " screen").isEqualTo(403);
            assertThat(get(path(key) + "/export?month=" + month(), teacher).status()).as(key + " export").isEqualTo(403);
        }
    }

    @Test
    void aManagerWithoutTheExportGrantIsRefusedTheFileButStillSeesTheScreen() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        String token = manager.signed().token();
        for (String key : List.of("manager", "teacher", "recruitment")) {
            String module = "REPORT_" + key.toUpperCase();
            String grant = "/api/v1/identity/permission-matrix/MANAGER/" + module + "/EXPORT";
            assertThat(put(grant, admin, Map.of("granted", false)).status()).as(key).isEqualTo(200);
            try {
                assertThat(get(path(key) + "/export?month=" + month(), token).status()).as(key + " export").isEqualTo(403);
                assertThat(get(path(key) + "?month=" + month(), token).status()).as(key + " screen").isEqualTo(200);
            } finally {
                put(grant, admin, Map.of("granted", true));
            }
        }
    }

    @Test
    void aManagerNameThatStartsLikeAFormulaIsNeutralisedInTheFile() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        jdbc.update("update app_user set display_name = ? where id = ?", "=1+1 Manager", manager.signed().userId());

        Resp csv = get(path("manager") + "/export?month=" + month() + "&zoneId=" + zone, admin);

        assertThat(csv.status()).as(csv.body()).isEqualTo(200);
        assertThat(csv.body()).contains("'=1+1 Manager");
        assertThat(csv.body()).doesNotContain("\"=1+1 Manager");
    }
}
