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

/** Spec 028 US8: the Zone changes only zone-aware tiles, the period only month tiles, and bad choices are refused. */
@Import(ZonePeriodFilterTest.EchoTiles.class)
class ZonePeriodFilterTest extends MasterDataTestBase {

    private static final String AREA = "/api/v1/dashboard/areas/RECRUITMENT";

    @TestConfiguration
    static class EchoTiles {
        @Bean
        TileSource echoTiles() {
            return new TileSource() {
                public List<TileDefinition> tiles() {
                    return List.of(
                            def("zp.month", 90, PeriodKind.MONTH, true),
                            def("zp.today", 91, PeriodKind.TODAY, false));
                }

                public TileResult read(TileDefinition tile, TileContext ctx) {
                    String text = tile.key().equals("zp.month")
                            ? ctx.month() + "|" + ctx.zoneId().map(UUID::toString).orElse("none")
                            : ctx.today().toString();
                    return TileResult.ok(tile.key(), Value.text("echo", text, tile.link()));
                }
            };
        }

        private static TileDefinition def(String key, int order, PeriodKind period, boolean zoneAware) {
            return new TileDefinition(
                    key, Area.RECRUITMENT, order, key, period, "TASKS", PermissionAction.VIEW, ScopeKind.ALL, zoneAware,
                    Link.to("/x"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> tiles(Resp resp) {
        Map<String, Map<String, Object>> byKey = new java.util.LinkedHashMap<>();
        for (Map<String, Object> t : (List<Map<String, Object>>) resp.map().get("tiles")) {
            byKey.put((String) t.get("key"), t);
        }
        return byKey;
    }

    @SuppressWarnings("unchecked")
    private static String echo(Map<String, Object> tile) {
        return (String) ((List<Map<String, Object>>) tile.get("values")).get(0).get("value");
    }

    @Test
    void theZoneChangesOnlyZoneAwareTilesAndMarksTheOthersOrganizationWide() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);

        Map<String, Map<String, Object>> plain = tiles(get(AREA, admin));
        Map<String, Map<String, Object>> zoned = tiles(get(AREA + "?zoneId=" + zone, admin));

        assertThat(echo(plain.get("zp.month"))).endsWith("|none");
        assertThat(echo(zoned.get("zp.month"))).endsWith("|" + zone);
        assertThat(zoned.get("zp.month").get("organizationWide")).isEqualTo(false);
        assertThat(zoned.get("zp.today").get("organizationWide")).isEqualTo(true);
        assertThat(echo(zoned.get("zp.today"))).isEqualTo(echo(plain.get("zp.today")));
    }

    @Test
    void thePeriodChangesMonthTilesAndIsIgnoredByTodayTiles() {
        String admin = signInAs(Role.ADMIN).token();

        Map<String, Map<String, Object>> plain = tiles(get(AREA, admin));
        Map<String, Map<String, Object>> march = tiles(get(AREA + "?period=2026-03", admin));

        assertThat(echo(march.get("zp.month"))).startsWith("2026-03|");
        assertThat(echo(march.get("zp.today"))).isEqualTo(echo(plain.get("zp.today")));
    }

    @Test
    void anUnreachableZoneIs404AndTheManagerTableAndInboxRespectTheChoices() {
        String admin = signInAs(Role.ADMIN).token();
        UUID mine = zone(admin);
        UUID other = zone(admin);
        ManagerCtx manager = newManager(admin, mine);

        assertThat(get(AREA + "?zoneId=" + other, manager.token()).status()).isEqualTo(404);
        assertThat(get(AREA + "?zoneId=" + mine, manager.token()).status()).isEqualTo(200);

        Resp table = get("/api/v1/dashboard/managers?zoneId=" + mine + "&period=2026-03", admin);
        assertThat(table.status()).as(table.body()).isEqualTo(200);
        assertThat(table.body()).contains("table");
        assertThat(get("/api/v1/dashboard/managers?zoneId=" + UUID.randomUUID(), admin).status()).isEqualTo(404);
        assertThat(get("/api/v1/dashboard/managers?period=banana", admin).status()).isEqualTo(400);
        assertThat(get("/api/v1/dashboard/inbox?period=2026-03", admin).status()).isEqualTo(200);
    }
}
