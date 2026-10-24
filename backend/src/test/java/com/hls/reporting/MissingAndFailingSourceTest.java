package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.State;
import com.hls.reporting.api.TileSource;
import com.hls.reporting.internal.AreaReader;
import com.hls.reporting.internal.RequestTileContext;
import com.hls.reporting.internal.TileRegistry;
import com.hls.support.MasterDataTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spec 028 SC-005: for every registered tile, a source that throws, one that exceeds the time limit and one whose optional
 * provider is missing each give an ERROR or NOT_AVAILABLE result with no figures, never OK with 0; the other tiles still read.
 */
class MissingAndFailingSourceTest extends MasterDataTestBase {

    @Autowired
    TileRegistry registry;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    Clock clock;

    private TileContext ctx() {
        return new RequestTileContext(
                UUID.randomUUID(), Set.of(Role.ADMIN), LocalDate.now(clock), YearMonth.now(clock), Optional.empty());
    }

    private List<TileRegistry.Entry> everyTile() {
        return registry.manifestFor(Set.of(Role.ADMIN));
    }

    private static TileRegistry.Entry replaced(TileRegistry.Entry entry, TileSource source) {
        return new TileRegistry.Entry(source, entry.definition());
    }

    private static TileSource throwing() {
        return new TileSource() {
            public List<TileDefinition> tiles() {
                return List.of();
            }

            public TileResult read(TileDefinition tile, TileContext ctx) {
                throw new IllegalStateException("source is down");
            }
        };
    }

    private static TileSource sleeping() {
        return new TileSource() {
            public List<TileDefinition> tiles() {
                return List.of();
            }

            public TileResult read(TileDefinition tile, TileContext ctx) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return TileResult.ok(tile.key(), TileResult.Value.count("late", 0, null));
            }
        };
    }

    private static TileSource missingProvider() {
        return new TileSource() {
            public List<TileDefinition> tiles() {
                return List.of();
            }

            public TileResult read(TileDefinition tile, TileContext ctx) {
                return TileResult.notAvailable(tile.key(), "Source is not installed");
            }
        };
    }

    private static void assertNeverZero(TileResult result) {
        assertThat(result.state()).isIn(State.ERROR, State.NOT_AVAILABLE);
        assertThat(result.values()).isEmpty();
        assertThat(result.table()).isNull();
    }

    @Test
    void theCatalogIsRegistered() {
        assertThat(everyTile()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void oneSourceThrowingIsOneErrorTileAndTheOthersStillRead() {
        AreaReader reader = new AreaReader(transactions, clock, 5000);
        List<TileRegistry.Entry> all = everyTile();
        List<TileResult> baseline = reader.read(all, ctx());
        for (int i = 0; i < all.size(); i++) {
            List<TileRegistry.Entry> mixed = new ArrayList<>(all);
            mixed.set(i, replaced(all.get(i), throwing()));
            List<TileResult> results = reader.read(mixed, ctx());
            assertThat(results).hasSize(all.size());
            assertNeverZero(results.get(i));
            for (int j = 0; j < all.size(); j++) {
                if (j != i) {
                    assertThat(results.get(j).state())
                            .as("%s while %s fails", all.get(j).definition().key(), all.get(i).definition().key())
                            .isEqualTo(baseline.get(j).state());
                    assertThat(results.get(j).values()).isEqualTo(baseline.get(j).values());
                }
            }
        }
    }

    @Test
    void everySourceThrowingGivesOnlyErrorTiles() {
        AreaReader reader = new AreaReader(transactions, clock, 5000);
        List<TileRegistry.Entry> broken = everyTile().stream().map(e -> replaced(e, throwing())).toList();
        reader.read(broken, ctx()).forEach(r -> {
            assertNeverZero(r);
            assertThat(r.state()).isEqualTo(State.ERROR);
        });
    }

    @Test
    void everySourceExceedingTheLimitGivesOnlyErrorTilesWithinTheLimit() {
        AreaReader reader = new AreaReader(transactions, clock, 400);
        List<TileRegistry.Entry> slow = everyTile().stream().map(e -> replaced(e, sleeping())).toList();
        long started = System.nanoTime();
        List<TileResult> results = reader.read(slow, ctx());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        results.forEach(r -> {
            assertNeverZero(r);
            assertThat(r.state()).isEqualTo(State.ERROR);
        });
        assertThat(elapsedMs).isLessThan(5000);
    }

    @Test
    void oneSlowSourceDoesNotHoldBackItsNeighbours() {
        AreaReader reader = new AreaReader(transactions, clock, 800);
        List<TileRegistry.Entry> all = everyTile();
        List<TileRegistry.Entry> mixed = new ArrayList<>(all);
        mixed.set(0, replaced(all.get(0), sleeping()));
        List<TileResult> results = reader.read(mixed, ctx());
        assertNeverZero(results.get(0));
        assertThat(results.subList(1, results.size())).allSatisfy(r -> assertThat(r.state()).isNotEqualTo(State.ERROR));
    }

    @Test
    void aMissingOptionalProviderIsNotAvailableForEveryTile() {
        AreaReader reader = new AreaReader(transactions, clock, 5000);
        List<TileRegistry.Entry> missing = everyTile().stream().map(e -> replaced(e, missingProvider())).toList();
        reader.read(missing, ctx()).forEach(r -> {
            assertNeverZero(r);
            assertThat(r.state()).isEqualTo(State.NOT_AVAILABLE);
            assertThat(r.note()).isNotBlank();
        });
    }
}
