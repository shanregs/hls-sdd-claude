package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.ReceivablesTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Spec 014b US4: the Margin, Collections and School reports export through the 014a writer. The file carries what the screen
 * shows, every export writes exactly one audit row, a caller without the Export grant is refused the file, and a Teacher or a
 * System user gets neither screen nor file.
 */
class SchoolReportsExportTest extends ReceivablesTestBase {

    /** Report key and the title the CSV carries on its first line. */
    private static final Map<String, String> TITLES = Map.of(
            "margin", "Margin",
            "collections", "Collections",
            "school", "School");

    private static final String BOM = "﻿";

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
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        String query = "?month=" + thisMonth() + "&schoolId=" + f.schoolId();

        for (var report : TITLES.entrySet()) {
            String key = report.getKey();
            int before = exportAuditRows(admin, key);

            Resp screen = get(path(key) + query, admin);
            Resp csv = get(path(key) + "/export" + query, admin);

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
    void aTeacherAndASystemUserGetNeitherTheScreenNorTheFileOfAnyOfTheThree() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (String key : TITLES.keySet()) {
                assertThat(get(path(key) + "?month=" + thisMonth(), token).status()).as(role + " " + key + " screen").isEqualTo(403);
                assertThat(get(path(key) + "/export?month=" + thisMonth(), token).status()).as(role + " " + key + " export").isEqualTo(403);
            }
        }
    }

    @Test
    void aManagerWithoutTheExportGrantIsRefusedTheFileButStillSeesTheScreen() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        String token = f.manager().signed().token();
        for (String key : List.of("margin", "collections", "school")) {
            String grant = "/api/v1/identity/permission-matrix/MANAGER/REPORT_" + key.toUpperCase() + "/EXPORT";
            assertThat(put(grant, admin, Map.of("granted", false)).status()).as(key).isEqualTo(200);
            try {
                assertThat(get(path(key) + "/export?month=" + thisMonth(), token).status()).as(key + " export").isEqualTo(403);
                assertThat(get(path(key) + "?month=" + thisMonth(), token).status()).as(key + " screen").isEqualTo(200);
            } finally {
                put(grant, admin, Map.of("granted", true));
            }
        }
    }

    @Test
    void aManagerNameThatStartsLikeAFormulaIsNeutralisedInTheFile() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        jdbc.update("update app_user set display_name = ? where id = ?", "=1+1 Manager", f.manager().signed().userId());

        Resp csv = get(path("school") + "/export?month=" + thisMonth() + "&schoolId=" + f.schoolId(), admin);

        assertThat(csv.status()).as(csv.body()).isEqualTo(200);
        assertThat(csv.body()).doesNotContain("\"=1+1 Manager");
    }
}
