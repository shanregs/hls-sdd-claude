package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 8 (FR-003): per-row bulk import with already-exists skipping and up-front rejections. */
class PlaceBulkImportTest extends MasterDataTestBase {

    private Map<String, Object> row(String name, String pin) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("name", name);
        row.put("pinCode", pin);
        return row;
    }

    private Resp importRows(String token, UUID zoneId, List<Map<String, Object>> rows) {
        return post("/api/v1/places/bulk-import", token, Map.of("zoneId", zoneId, "rows", rows));
    }

    @Test
    void validRowsInvalidRowsAndRepeatsAreClassifiedPerRow() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        String existingName = uniqueName("Existing");
        String existingPin = nextPin();
        post("/api/v1/zones/" + zoneId + "/places", admin, Map.of("name", existingName, "pinCode", existingPin));
        String fresh = uniqueName("Fresh");
        String freshPin = nextPin();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(fresh, freshPin)); // 1 added
        rows.add(row(existingName.toUpperCase(), existingPin)); // 2 repeat of an existing place (case-insensitive)
        rows.add(row(fresh, freshPin)); // 3 repeat earlier in the same list
        rows.add(row(fresh, nextPin())); // 4 same name, different PIN: added
        rows.add(row(uniqueName("SamePin"), freshPin)); // 5 same PIN, different name: added
        rows.add(row("", nextPin())); // 6 missing name
        rows.add(row(uniqueName("BadPin"), "12345")); // 7 bad PIN
        rows.add(row(uniqueName("NoPin"), null)); // 8 missing PIN

        Resp resp = importRows(admin, zoneId, rows);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("added", 3).containsEntry("alreadyExisted", 2).containsEntry("rejected", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) resp.map().get("results");
        assertThat(results).hasSize(8);
        assertThat(results.get(0)).containsEntry("row", 1).containsEntry("outcome", "ADDED");
        assertThat(results.get(1)).containsEntry("outcome", "ALREADY_EXISTS");
        assertThat(results.get(2)).containsEntry("outcome", "ALREADY_EXISTS");
        assertThat(results.get(5)).containsEntry("outcome", "REJECTED").containsEntry("reason", "Place name is required.");
        assertThat(results.get(6)).containsEntry("reason", "PIN code must be six digits.");
        assertThat(total(get("/api/v1/zones/" + zoneId + "/places", admin))).isEqualTo(4);
    }

    @Test
    void reImportingTheSameListAddsNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        List<Map<String, Object>> rows = List.of(row(uniqueName("A"), nextPin()), row(uniqueName("B"), nextPin()));
        assertThat(importRows(admin, zoneId, rows).map()).containsEntry("added", 2);

        Resp again = importRows(admin, zoneId, rows);

        assertThat(again.map()).containsEntry("added", 0).containsEntry("alreadyExisted", 2);
        assertThat(total(get("/api/v1/zones/" + zoneId + "/places", admin))).isEqualTo(2);
    }

    @Test
    void emptyOversizeAndUnknownZoneAreRejectedUpFrontCreatingNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            tooMany.add(row("P" + i, "100000"));
        }

        assertThat(importRows(admin, zoneId, List.of()).status()).isEqualTo(400);
        Resp oversize = importRows(admin, zoneId, tooMany);
        assertThat(oversize.status()).isEqualTo(400);
        assertThat(oversize.body()).contains("more than 5000 rows");
        assertThat(importRows(admin, UUID.randomUUID(), List.of(row("X", "123456"))).status())
                .isEqualTo(400);
        assertThat(total(get("/api/v1/zones/" + zoneId + "/places", admin))).isZero();
    }

    @Test
    void aThousandRowsImportInWellUnderThirtySecondsAndAreAudited() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(row("Village " + i + " " + UUID.randomUUID().toString().substring(0, 6), nextPin()));
        }

        long start = System.nanoTime();
        Resp resp = importRows(admin, zoneId, rows);
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("added", 1000);
        assertThat(seconds).isLessThan(30);
        assertThat(total(get("/api/v1/zones/" + zoneId + "/places", admin))).isEqualTo(1000);
    }

    @Test
    void directorMayImportButManagerTeacherAndSystemAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);

        assertThat(importRows(signInAs(Role.DIRECTOR).token(), zoneId, List.of(row(uniqueName("D"), nextPin())))
                        .status())
                .isEqualTo(200);
        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER, Role.SYSTEM}) {
            assertThat(importRows(signInAs(role).token(), zoneId, List.of(row("X", "123456")))
                            .status())
                    .isEqualTo(403);
        }
    }
}
