package com.hls.reporting.internal;

import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads the tiles of an area, each in its own read-only transaction and within a time limit, so that one source throwing or
 * running slowly becomes one {@code ERROR} tile and never a failed area (spec 028 FR-013, SC-005). A figure is never
 * defaulted to 0.
 */
@Component
public class AreaReader implements DisposableBean {

    static final String ERROR_NOTE = "Could not be read";
    private static final Logger LOG = LoggerFactory.getLogger(AreaReader.class);

    private final TransactionTemplate readOnly;
    private final Clock clock;
    private final long limitMillis;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AreaReader(
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${hls.dashboard.tile-limit-ms:2500}") long limitMillis) {
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
        this.limitMillis = limitMillis;
    }

    /** Reads the entries in parallel; the result list has one result per entry, in the same order. */
    public List<TileResult> read(List<TileRegistry.Entry> entries, TileContext ctx) {
        Instant takenAt = Instant.now(clock);
        boolean zoneChosen = ctx.zoneId().isPresent();
        long startedAt = System.nanoTime();
        List<Future<TileResult>> futures = new ArrayList<>();
        for (TileRegistry.Entry entry : entries) {
            futures.add(executor.submit(() -> readOnly.execute(status -> entry.source().read(entry.definition(), ctx))));
        }
        List<TileResult> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TileRegistry.Entry entry = entries.get(i);
            String key = entry.definition().key();
            TileResult result = await(futures.get(i), key, startedAt);
            boolean organizationWide = zoneChosen && !entry.definition().zoneAware();
            results.add(result.stamped(takenAt, organizationWide));
        }
        return results;
    }

    /** Reads one tile (the retry after an error). */
    public TileResult readOne(TileRegistry.Entry entry, TileContext ctx) {
        return read(List.of(entry), ctx).get(0);
    }

    private TileResult await(Future<TileResult> future, String key, long startedAt) {
        long remaining = limitMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        try {
            TileResult result = future.get(Math.max(remaining, 1), TimeUnit.MILLISECONDS);
            if (result == null || !key.equals(result.key())) {
                LOG.warn("Dashboard tile {} returned no result or one with another key", key);
                return TileResult.error(key, ERROR_NOTE);
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.warn("Dashboard tile {} exceeded {} ms", key, limitMillis);
            return TileResult.error(key, ERROR_NOTE);
        } catch (ExecutionException e) {
            LOG.warn("Dashboard tile {} failed", key, e.getCause());
            return TileResult.error(key, ERROR_NOTE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TileResult.error(key, ERROR_NOTE);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
