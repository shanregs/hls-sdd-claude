package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import com.hls.support.MasterDataTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spec 028 SC-004: the manifest and area endpoints per role, 404 / 400 / 401 cases and no audit rows, against a fixed test tile. */
@Import(DashboardEndpointsTest.FixedTiles.class)
class DashboardEndpointsTest extends MasterDataTestBase {

    private static final String BASE = "/api/v1/dashboard";

    @TestConfiguration
    static class FixedTiles {
        @Bean
        TileSource fixedTiles() {
            return new TileSource() {
                @Override
                public List<TileDefinition> tiles() {
                    return List.of(
                            def("test.fixed", Area.MANAGERS, 1, ScopeKind.ALL, true),
                            def("test.global", Area.MANAGERS, 2, ScopeKind.ALL, false),
                            def("test.failing", Area.MANAGERS, 3, ScopeKind.ALL, true),
                            def("test.orgwide", Area.SCHOOLS, 1, ScopeKind.ORG_WIDE_ONLY, false));
                }

                @Override
                public TileResult read(TileDefinition tile, TileContext ctx) {
                    if (tile.key().equals("test.failing")) {
                        throw new IllegalStateException("source is down");
                    }
                    return TileResult.ok(tile.key(), Value.count("Fixed", 42, tile.link()));
                }
            };
        }

        private static TileDefinition def(String key, Area area, int order, ScopeKind scope, boolean zoneAware) {
            return new TileDefinition(
                    key, area, order, key, PeriodKind.TODAY, "TASKS", PermissionAction.VIEW, scope, zoneAware,
                    Link.to("/tasks"));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> areas(Resp manifest) {
        return (List<Map<String, Object>>) manifest.map().get("areas");
    }

    private static List<String> areaNames(Resp manifest) {
        return areas(manifest).stream().map(a -> (String) a.get("area")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> tilesByKey(Resp area) {
        Map<String, Map<String, Object>> byKey = new java.util.LinkedHashMap<>();
        for (Map<String, Object> tile : (List<Map<String, Object>>) area.map().get("tiles")) {
            byKey.put((String) tile.get("key"), tile);
        }
        return byKey;
    }

    @Test
    void adminAndDirectorSeeEveryAreaWithItsTilesInOrder() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            Resp manifest = get(BASE, signInAs(role).token());
            assertThat(manifest.status()).as(manifest.body()).isEqualTo(200);
            assertThat(areaNames(manifest)).contains("MANAGERS", "SCHOOLS").doesNotContain("PAYROLL");
            assertThat(manifest.body()).contains("test.fixed", "test.orgwide").doesNotContain("permissionModule");
            assertThat(manifest.map()).containsKey("filters").containsKey("inbox");
        }
    }

    @Test
    void aManagerSeesOnlyTheTilesThatAreNotOrgWideOnly() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        Resp manifest = get(BASE, manager.token());
        assertThat(manifest.status()).isEqualTo(200);
        assertThat(areaNames(manifest)).contains("MANAGERS");
        assertThat(manifest.body()).doesNotContain("test.orgwide").doesNotContain("managers.comparison");
        assertThat(get(BASE + "/areas/SCHOOLS/tiles/test.orgwide", manager.token()).status()).isEqualTo(404);
    }

    @Test
    void teacherAndSystemGetAnEmptyManifestAndNothingElse() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            Resp manifest = get(BASE, token);
            assertThat(manifest.status()).isEqualTo(200);
            assertThat(areas(manifest)).as(role.name()).isEmpty();
            assertThat(manifest.map().get("inbox")).isEqualTo(false);
            assertThat(get(BASE + "/areas/MANAGERS", token).status()).isEqualTo(404);
            assertThat(get(BASE + "/areas/MANAGERS/tiles/test.fixed", token).status()).isEqualTo(404);
        }
    }

    @Test
    void anAreaReturnsEachTileAndOneFailingTileDoesNotFailTheArea() {
        Resp area = get(BASE + "/areas/MANAGERS", signInAs(Role.ADMIN).token());
        assertThat(area.status()).as(area.body()).isEqualTo(200);
        assertThat(area.map()).containsKey("takenAt");
        Map<String, Map<String, Object>> tiles = tilesByKey(area);
        assertThat(tiles.keySet().stream().filter(k -> k.startsWith("test.")))
                .containsExactly("test.fixed", "test.global", "test.failing");
        assertThat(tiles.get("test.fixed").get("state")).isEqualTo("OK");
        assertThat(tiles.get("test.failing").get("state")).isEqualTo("ERROR");
        assertThat((List<?>) tiles.get("test.failing").get("values")).isEmpty();
        assertThat(tiles.get("test.failing").get("note")).isEqualTo("Could not be read");
    }

    @Test
    void oneTileCanBeReadAgainAndAnUnknownTileOrAreaIs404() {
        String admin = signInAs(Role.ADMIN).token();
        Resp tile = get(BASE + "/areas/MANAGERS/tiles/test.fixed", admin);
        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        assertThat(get(BASE + "/areas/MANAGERS/tiles/nope", admin).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/SCHOOLS/tiles/test.fixed", admin).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/NOPE", admin).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/PAYROLL", admin).status()).isEqualTo(404);
    }

    @Test
    void aBadPeriodIs400AndAGoodOneIsAccepted() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(get(BASE + "/areas/MANAGERS?period=2026-13", admin).status()).isEqualTo(400);
        assertThat(get(BASE + "/areas/MANAGERS?period=banana", admin).status()).isEqualTo(400);
        assertThat(get(BASE + "/areas/MANAGERS?period=2026-09", admin).status()).isEqualTo(200);
    }

    @Test
    void anUnreachableOrUnknownZoneIs404AndAReachableOneMarksTilesThatCannotBeSplit() {
        String admin = signInAs(Role.ADMIN).token();
        UUID mine = zone(admin);
        UUID other = zone(admin);
        ManagerCtx manager = newManager(admin, mine);

        assertThat(get(BASE + "/areas/MANAGERS?zoneId=" + other, manager.token()).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/MANAGERS?zoneId=" + UUID.randomUUID(), admin).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/MANAGERS?zoneId=not-a-zone", admin).status()).isEqualTo(404);

        Resp area = get(BASE + "/areas/MANAGERS?zoneId=" + mine, manager.token());
        assertThat(area.status()).as(area.body()).isEqualTo(200);
        Map<String, Map<String, Object>> tiles = tilesByKey(area);
        assertThat(tiles.get("test.fixed").get("organizationWide")).isEqualTo(false);
        assertThat(tiles.get("test.global").get("organizationWide")).isEqualTo(true);
        assertThat(get(BASE + "/areas/MANAGERS?zoneId=" + other, admin).status()).isEqualTo(200);
    }

    @Test
    void theZoneFilterListsOnlyTheZonesTheCallerMayPick() {
        String admin = signInAs(Role.ADMIN).token();
        UUID mine = zone(admin);
        UUID other = zone(admin);
        ManagerCtx manager = newManager(admin, mine);
        assertThat(get(BASE, manager.token()).body()).contains(mine.toString()).doesNotContain(other.toString());
        assertThat(get(BASE, admin).body()).contains(mine.toString(), other.toString());
    }

    @Test
    void anonymousCallersGet401() {
        assertThat(get(BASE, null).status()).isEqualTo(401);
        assertThat(get(BASE + "/areas/MANAGERS", null).status()).isEqualTo(401);
    }

    @Test
    void readingTheDashboardWritesNoAuditRows() {
        String admin = signInAs(Role.ADMIN).token();
        int changes = count("change_history_entry");
        int activity = count("user_activity_entry");
        get(BASE, admin);
        get(BASE + "/areas/MANAGERS", admin);
        get(BASE + "/areas/MANAGERS/tiles/test.fixed", admin);
        assertThat(count("change_history_entry")).isEqualTo(changes);
        assertThat(count("user_activity_entry")).isEqualTo(activity);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
