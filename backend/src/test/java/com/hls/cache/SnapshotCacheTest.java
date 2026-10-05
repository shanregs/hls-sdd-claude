package com.hls.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SnapshotCacheTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));

        void advance(Duration by) {
            now.updateAndGet(i -> i.plus(by));
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger source = new AtomicInteger(1);

    private SnapshotCache<Integer> cache(boolean enabled) {
        return new SnapshotCache<>("test", source::get, Duration.ofSeconds(60), clock, enabled);
    }

    @Test
    void loadsOnceAndThenServesFromMemory() {
        SnapshotCache<Integer> cache = cache(true);

        assertThat(cache.get()).isEqualTo(1);
        source.set(2); // a change nobody told the cache about
        assertThat(cache.get()).isEqualTo(1);
        assertThat(cache.loads()).isEqualTo(1);
    }

    @Test
    void reloadsAfterTheTimeLimit() {
        SnapshotCache<Integer> cache = cache(true);
        cache.get();
        source.set(2);

        clock.advance(Duration.ofSeconds(59));
        assertThat(cache.get()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(2));
        assertThat(cache.get()).isEqualTo(2);
        assertThat(cache.loads()).isEqualTo(2);
    }

    @Test
    void invalidateMakesTheNextReadLoadAgain() {
        SnapshotCache<Integer> cache = cache(true);
        cache.get();
        source.set(2);

        cache.invalidate();

        assertThat(cache.get()).isEqualTo(2);
    }

    @Test
    void whenDisabledEveryReadLoads() {
        SnapshotCache<Integer> cache = cache(false);

        cache.get();
        source.set(2);

        assertThat(cache.get()).isEqualTo(2);
        assertThat(cache.loads()).isEqualTo(2);
    }

    @Test
    void aLoadThatRacedWithAnInvalidationIsNotStored() {
        AtomicReference<SnapshotCache<Integer>> self = new AtomicReference<>();
        // The loader reads the old value, then a writer commits and invalidates before the load returns.
        SnapshotCache<Integer> cache = new SnapshotCache<>(
                "race",
                () -> {
                    int seen = source.get();
                    source.set(2);
                    self.get().invalidate();
                    return seen;
                },
                Duration.ofSeconds(60),
                clock,
                true);
        self.set(cache);

        assertThat(cache.get()).isEqualTo(1); // the caller still gets what it read
        assertThat(cache.get()).isEqualTo(2); // but the stale value was not kept
    }

    @Test
    void concurrentReadersShareOneLoad() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loaded = new AtomicInteger();
        SnapshotCache<Integer> cache = new SnapshotCache<>(
                "shared",
                () -> {
                    loaded.incrementAndGet();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return 7;
                },
                Duration.ofSeconds(60),
                clock,
                true);

        Thread[] readers = new Thread[8];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(cache::get);
            readers[i].start();
        }
        Thread.sleep(150);
        release.countDown();
        for (Thread reader : readers) {
            reader.join();
        }

        assertThat(loaded.get()).isEqualTo(1);
    }

    @Test
    void invalidateAroundDropsTheCopyNowAndAgainWhenTheTransactionEnds() {
        SnapshotCache<Integer> cache = cache(true);
        cache.get();
        TransactionSynchronizationManager.initSynchronization();
        try {
            source.set(2);
            cache.invalidateAround();
            assertThat(cache.get()).isEqualTo(2); // dropped at once; a reader inside the transaction reloads

            source.set(3); // the writer changed it again before committing
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(cache.get()).isEqualTo(3);
    }

    @Test
    void invalidateAroundAlsoDropsTheCopyWhenTheTransactionRollsBack() {
        SnapshotCache<Integer> cache = cache(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            source.set(99); // an uncommitted change that a reader inside the transaction would see
            cache.invalidateAround();
            assertThat(cache.get()).isEqualTo(99);

            source.set(1); // rolled back
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(cache.get()).isEqualTo(1);
    }

    @Test
    void invalidateAroundOutsideATransactionJustDropsTheCopy() {
        SnapshotCache<Integer> cache = cache(true);
        cache.get();
        source.set(2);

        cache.invalidateAround();

        assertThat(cache.get()).isEqualTo(2);
    }
}
