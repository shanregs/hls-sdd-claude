package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 027 T063 and T064: two writers at once, and the input and scope protections. */
class MovementConcurrencyAndSecurityTest extends TransferTestBase {

    private List<Integer> inParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Integer>> futures = pool.invokeAll(calls);
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void twoDecidersApprovingAtOnceOneSucceedsAndTheOtherGetsA409() throws Exception {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        long version = versionOf(w.director(), request);
        Callable<Integer> byDirector = () -> post(API + "/" + request + "/approve", w.director(), Map.of("version", version)).status();
        Callable<Integer> byManager = () -> post(API + "/" + request + "/approve", w.managerA().token(), Map.of("version", version)).status();
        List<Integer> results = inParallel(List.of(byDirector, byManager));
        assertThat(results).containsExactlyInAnyOrder(200, 409);
        assertThat(rows(w.t1())).hasSize(2);
    }

    @Test
    void twoRequestsForTheSameTeacherAtOnceOneSucceeds() throws Exception {
        World w = world();
        Callable<Integer> first = () -> raiseTransfer(w.admin(), w.t1(), w.schoolB1(), in(3)).status();
        Callable<Integer> second = () -> raiseTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(4)).status();
        List<Integer> results = inParallel(List.of(first, second));
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from movement_request where teacher_id = ?", Integer.class, w.t1())).isEqualTo(1);
    }

    @Test
    void textLongerThanTheLimitIsRefusedAndOutputIsReturnedAsPlainJsonText() {
        World w = world();
        Map<String, Object> body = new HashMap<>(transferBody(w.t1(), w.schoolB1(), in(3)));
        body.put("note", "x".repeat(1001));
        assertThat(post(API + "/transfers", w.admin(), body).status()).isEqualTo(400);
        body.put("note", "<script>alert(1)</script>");
        Resp created = post(API + "/transfers", w.admin(), body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        // returned as data inside JSON (the client escapes it on output); never as markup with a content type of html
        assertThat(created.map().get("note")).isEqualTo("<script>alert(1)</script>");
        Map<String, Object> reason = new HashMap<>();
        reason.put("version", versionOf(w.admin(), created.id()));
        reason.put("reason", "y".repeat(1001));
        assertThat(post(API + "/" + created.id() + "/cancel", w.admin(), reason).status()).isEqualTo(400);
    }

    @Test
    void noIdentifierLeaksAcrossScope() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        World other = world();
        String stranger = other.managerA().token();
        assertThat(get(API + "/" + request, stranger).status()).isEqualTo(404);
        assertThat(get(API + "/" + UUID.randomUUID(), stranger).status()).isEqualTo(404);
        assertThat(get(API + "/teachers/" + w.t1() + "/movements", stranger).status()).isEqualTo(404);
        assertThat(get(API + "/schools/" + w.schoolA1() + "/movements", stranger).status()).isEqualTo(404);
        assertThat(get(API + "/candidates?schoolId=" + w.schoolA1() + "&on=" + in(3), stranger).status()).isEqualTo(404);
        assertThat(raiseTransfer(stranger, w.t1(), w.schoolB1(), in(5)).status()).isEqualTo(404);
        Map<String, Object> check = new HashMap<>(transferBody(w.t1(), w.schoolB1(), in(3)));
        check.put("kind", "TRANSFER");
        assertThat(post(API + "/check", stranger, check).status()).isEqualTo(404);
    }

    @Test
    void selfDecisionAndAdminDecisionAreRefusedOnEveryDecideEndpoint() {
        World w = world();
        UUID byManager = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.managerA().token(), byManager).status()).isEqualTo(403);
        assertThat(reject(w.managerA().token(), byManager, "Self").status()).isEqualTo(403);
        assertThat(approve(w.admin(), byManager).status()).isEqualTo(403);
        assertThat(reject(w.admin(), byManager, "Admin").status()).isEqualTo(403);
    }
}
