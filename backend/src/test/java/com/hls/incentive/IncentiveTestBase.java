package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.MouTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Helpers for the spec 030 tests: a School with an ACTIVE MoU and a Zone Manager (spec 031 helpers), a won prospect for it
 * owned by someone chosen by the test, the MoU approved through the real endpoints, and waits for the after-commit
 * listeners.
 */
public abstract class IncentiveTestBase extends MouTestBase {

    protected static final String API = "/api/v1/incentives";

    @Autowired
    protected JdbcTemplate db;

    /** Inserts a won prospect linked to the School, owned by the user. */
    protected void wonProspect(UUID zoneId, UUID schoolId, UUID ownerUserId, UUID wonBy) {
        String name = "Won " + UUID.randomUUID();
        db.update(
                "insert into marketing_prospect (id, name, zone_id, name_key, owner_user_id, stage, won_at, won_by,"
                        + " school_id, version, created_by, created_at) values (?, ?, ?, ?, ?, 'FINAL_STAGE', now(), ?, ?, 0, ?, now())",
                UUID.randomUUID(), name, zoneId, name.toLowerCase(), ownerUserId, wonBy, schoolId, wonBy);
    }

    /** Submits as Admin and approves as the Director through the real endpoints. */
    protected void approveMou(Mou m) {
        Resp submitted = submit(m.admin(), m.contractId());
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        Resp approved = approve(m.director().token(), m.contractId());
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
    }

    /** A School whose MoU is approved and whose won prospect is owned by the given user. */
    protected Mou approvedMouOwnedBy(UUID ownerUserId) {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), ownerUserId, m.adminUserId());
        approveMou(m);
        return m;
    }

    /** Waits for the after-commit listeners of the events published so far (does not fail on a stuck one). */
    protected void settle() {
        try {
            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> db.queryForObject(
                                    "select count(*) from event_publication where completion_date is null", Integer.class)
                            == 0);
        } catch (RuntimeException ignored) {
            // a failed listener from another test must not fail this one; the assertions that follow decide
        }
    }

    protected Map<String, Object> incentiveOf(UUID schoolId) {
        List<Map<String, Object>> rows = db.queryForList("select * from incentive where school_id = ?", schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Waits until the School has an incentive and returns its row. */
    protected Map<String, Object> awaitIncentive(UUID schoolId) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> incentiveOf(schoolId) != null);
        return incentiveOf(schoolId);
    }

    protected Map<String, Object> noEarnerOf(UUID schoolId) {
        List<Map<String, Object>> rows = db.queryForList("select * from no_earner where school_id = ?", schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected Map<String, Object> awaitNoEarner(UUID schoolId) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> noEarnerOf(schoolId) != null);
        return noEarnerOf(schoolId);
    }

    protected int incentiveRows(UUID schoolId) {
        return db.queryForObject("select count(*) from incentive where school_id = ?", Integer.class, schoolId);
    }

    protected List<String> eventKinds(Object incentiveId) {
        return db.queryForList(
                "select kind from incentive_event where incentive_id = ? order by seq", String.class, incentiveId);
    }

    protected UUID idOf(Map<String, Object> row) {
        return (UUID) row.get("id");
    }

    protected String adminToken() {
        return signInAs(Role.ADMIN).token();
    }

    protected Resp setAmount(String adminToken, int rupees) {
        return put("/api/v1/settings/system", adminToken, Map.of("values", Map.of("incentive.amount-per-school", Integer.toString(rupees))));
    }
}
