package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Two simultaneous overlapping submissions by one Manager must not both be accepted. The overlap check is
 * a read followed by an insert, so submissions are serialized per Manager with a Postgres advisory lock.
 */
class ManagerLeaveSubmissionRaceTest extends ManagerLeaveTestBase {

    @Test
    void aSubmissionWaitsWhileAnotherSubmissionOfTheSameManagerIsInFlight() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        var pool = Executors.newSingleThreadExecutor();
        try (Connection holder = jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                lock.setString(1, "manager_leave:" + manager.managerId());
                lock.execute();
            }

            CompletableFuture<Resp> submitted =
                    CompletableFuture.supplyAsync(() -> ask(manager.token(), days[0], days[0]), pool);

            assertThat(waits(submitted)).as("the submission waits for the lock").isTrue();
            holder.commit();
            assertThat(submitted.get(30, TimeUnit.SECONDS).status()).isEqualTo(201);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoSimultaneousOverlappingSubmissionsYieldExactlyOneSuccess() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> call = () -> {
                start.await();
                return ask(manager.token(), days[0], days[1]).status();
            };
            var a = pool.submit(call);
            var b = pool.submit(call);
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean waits(CompletableFuture<?> future) throws Exception {
        try {
            future.get(1500, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        }
    }
}
