package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import com.hls.reporting.internal.TileRegistry;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spec 028 SC-006: a later spec adds a tile, a Manager column and an approval kind through a configuration alone. The tile
 * shows under its area for a caller with its grant, is absent (and 404) for one without, a duplicate key stops start-up, and
 * no existing tile changes.
 */
@Import(TileExtensibilityTest.ExtraSources.class)
class TileExtensibilityTest extends MasterDataTestBase {

    private static final String KEY = "ext.example";

    private static TileDefinition definition(String key) {
        // Security settings View is held by Admin only in the default matrix, so Director has no grant for this tile.
        return new TileDefinition(
                key, Area.RECRUITMENT, 99, "Example", PeriodKind.ALL_TIME, "SECURITY_SETTINGS", PermissionAction.VIEW,
                ScopeKind.ALL, false, Link.to("/example"));
    }

    @TestConfiguration
    static class ExtraSources {
        @Bean
        TileSource extensionTile() {
            return new TileSource() {
                public List<TileDefinition> tiles() {
                    return List.of(definition(KEY));
                }

                public TileResult read(TileDefinition tile, TileContext ctx) {
                    return TileResult.ok(tile.key(), Value.count("Example", 7, tile.link()));
                }
            };
        }

        @Bean
        ManagerColumns extensionColumns() {
            return new ManagerColumns() {
                public List<Column> columns() {
                    return List.of(new Column("ext.col", "Extension column", null));
                }

                public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
                    return Map.of();
                }
            };
        }

        @Bean
        ApprovalSource extensionApprovals() {
            return new ApprovalSource() {
                public String kind() {
                    return "EXT_KIND";
                }

                public String permissionModule() {
                    return "TASKS";
                }

                public PermissionAction action() {
                    return PermissionAction.APPROVE;
                }

                public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
                    return List.of(new ApprovalItem(
                            "EXT_KIND", UUID.randomUUID(), UUID.randomUUID(), "Someone", "Extension item", LocalDate.now(),
                            Link.to("/example")));
                }
            };
        }
    }

    @Test
    void theNewTileAppearsForACallerWithItsGrantAndIsAbsentForOneWithout() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();

        Resp withGrant = get("/api/v1/dashboard/areas/RECRUITMENT", admin);
        assertThat(withGrant.status()).as(withGrant.body()).isEqualTo(200);
        assertThat(withGrant.body()).contains(KEY);
        assertThat(get("/api/v1/dashboard/areas/RECRUITMENT/tiles/" + KEY, admin).status()).isEqualTo(200);
        assertThat(get("/api/v1/dashboard", admin).body()).contains(KEY);

        assertThat(get("/api/v1/dashboard", director).body()).doesNotContain(KEY);
        assertThat(get("/api/v1/dashboard/areas/RECRUITMENT/tiles/" + KEY, director).status()).isEqualTo(404);
        assertThat(get("/api/v1/dashboard/areas/RECRUITMENT", director).body()).doesNotContain(KEY);
    }

    @Test
    void theNewColumnAndApprovalKindJoinTheirTablesAndOtherTilesAreUnchanged() {
        Signed admin = signInAs(Role.ADMIN);

        assertThat(get("/api/v1/dashboard/managers", admin.token()).body()).contains("Extension column");
        Resp inbox = get("/api/v1/dashboard/inbox", admin.token());
        assertThat(inbox.status()).as(inbox.body()).isEqualTo(200);
        assertThat(inbox.body()).contains("EXT_KIND", "Extension item");

        // an existing tile reads the same through the area as it does on its own
        Resp area = get("/api/v1/dashboard/areas/TEACHERS", admin.token());
        Resp single = get("/api/v1/dashboard/areas/TEACHERS/tiles/teachers.byStatus", admin.token());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) area.map().get("tiles");
        Map<String, Object> fromArea = tiles.stream()
                .filter(t -> "teachers.byStatus".equals(t.get("key")))
                .findFirst()
                .orElseThrow();
        assertThat(fromArea.get("values")).isEqualTo(single.map().get("values"));
        assertThat(fromArea.get("state")).isEqualTo(single.map().get("state"));
    }

    @Test
    void aDuplicateKeyStopsStartUp() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        TileSource first = new Fixed();
        TileSource second = new Fixed();
        beans.registerSingleton("first", first);
        beans.registerSingleton("second", second);

        assertThatThrownBy(() -> new TileRegistry(beans.getBeanProvider(TileSource.class), mock(PermissionGuard.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KEY);
    }

    private static final class Fixed implements TileSource {
        public List<TileDefinition> tiles() {
            return List.of(definition(KEY));
        }

        public TileResult read(TileDefinition tile, TileContext ctx) {
            return TileResult.ok(tile.key(), Value.count("x", 1, null));
        }
    }
}
