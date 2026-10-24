package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 FR-012: the approver corrects commercial terms while the MoU is pending, and only then. */
class MouTermsAmendTest extends MouTestBase {

    private Map<String, Object> body(Mou m, Object... keyValues) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", versionOf(m.admin(), m.contractId()));
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return body;
    }

    private Map<String, Object> contractOf(Mou m) {
        return contractsOf(m.admin(), m.f().schoolId()).stream()
                .filter(c -> m.contractId().toString().equals(c.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theRateChangeUpdatesEveryPositionSalaryAndBothVersionsMove() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long contractVersion = ((Number) contractOf(m).get("version")).longValue();
        long approvalVersion = versionOf(m.admin(), m.contractId());

        Resp resp = patchTerms(tokenOf(m.director()), m.contractId(), body(m, "rate", "16000.50"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> contract = contractOf(m);
        assertThat(contract.get("rate")).isEqualTo("16000.50");
        assertThat(positionsOf(contract)).hasSize(2).allSatisfy(p -> assertThat(p.get("salary")).isEqualTo("16000.50"));
        assertThat(((Number) contract.get("version")).longValue()).isGreaterThan(contractVersion);
        assertThat(versionOf(m.admin(), m.contractId())).isGreaterThan(approvalVersion);
        assertThat(resp.map().get("contract").toString()).contains("16000.50");
        assertThat(resp.body()).contains("TERMS_AMENDED");
        assertThat(resp.body()).contains("rate: 15000.00 -> 16000.50");
        // old and new values are in Change History
        assertChangeRecorded(m.admin(), "CONTRACT", m.contractId(), "rate");
        String history = get("/api/v1/audit/change-history?entityType=CONTRACT&entityId=" + m.contractId() + "&size=100", m.admin())
                .body();
        assertThat(history).contains("15000").contains("16000.50");
    }

    @Test
    void aDeciderOnTheOldScreenGets409AfterTheTermsChanged() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long before = versionOf(m.admin(), m.contractId());
        patchTerms(m.managerToken(), m.contractId(), body(m, "rate", "16000"));

        Resp stale = post(BASE + "/contracts/" + m.contractId() + "/approve", tokenOf(m.director()), Map.of("version", before));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("PENDING");
    }

    @Test
    void positionSalariesChangeForADifferentSalaryMoU() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();
        UUID id = contractId(createContract(admin, f.schoolId(), perTeacherBody(f, dir.userId(), "1000", "2000")));
        Mou m = new Mou(f, admin, dir, id, null);
        submit(admin, id);

        Resp bad = patchTerms(tokenOf(dir), id, body(m, "rate", "5000"));
        Resp ok = patchTerms(
                tokenOf(dir), id, body(m, "positions", List.of(Map.of("number", 2, "salary", "2500.00"))));

        assertThat(bad.status()).isEqualTo(400);
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        List<Map<String, Object>> positions = positionsOf(contractOf(m));
        assertThat(positions.get(0).get("salary")).isEqualTo("1000.00");
        assertThat(positions.get(1).get("salary")).isEqualTo("2500.00");
        assertThat(patchTerms(tokenOf(dir), id, body(m, "positions", List.of(Map.of("number", 9, "salary", "1")))).status())
                .isEqualTo(400);
    }

    @Test
    void validationsOfTheMoUApply() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        String manager = m.managerToken();

        assertThat(patchTerms(manager, m.contractId(), body(m, "rate", "0")).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m, "rate", "10.123")).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m, "rate", "10000001")).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m, "signedOn", today.plusDays(1).toString())).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m, "endsOn", today.minusDays(1).toString())).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m)).status()).isEqualTo(400);
        assertThat(patchTerms(manager, m.contractId(), body(m, "rate", "15000")).status()).as("no change").isEqualTo(400);
        assertThat(contractOf(m).get("rate")).isEqualTo("15000.00");
    }

    @Test
    void theEditableDatesChangeAndTheOthersAreRefused() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        String manager = m.managerToken();
        LocalDate end = today.plusDays(200);

        Resp ok = patchTerms(
                manager, m.contractId(), body(m, "signedOn", today.minusDays(5).toString(), "endsOn", end.toString()));

        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(contractOf(m)).containsEntry("signedOn", today.minusDays(5).toString()).containsEntry("endsOn", end.toString());
        for (Map.Entry<String, Object> forbidden : Map.<String, Object>of(
                        "teacherCount", 5, "salaryMode", "PER_TEACHER", "signatories", List.of("x"), "schoolId", UUID.randomUUID())
                .entrySet()) {
            assertThat(patchTerms(manager, m.contractId(), body(m, forbidden.getKey(), forbidden.getValue())).status())
                    .as(forbidden.getKey())
                    .isEqualTo(400);
        }
    }

    @Test
    void thereIsNoEditOutsideThePendingPathAndTheDatabaseAgrees() {
        Mou m = mou();
        String dir = tokenOf(m.director());
        // DRAFT
        assertThat(patchTerms(dir, m.contractId(), body(m, "rate", "16000")).status()).isEqualTo(409);
        UUID position = UUID.fromString((String) positionsOf(contractOf(m)).get(0).get("id"));
        assertThatThrownBy(() -> jdbc.update("update contract_position set salary = 99 where id = ?", position))
                .hasMessageContaining("cannot be updated or deleted");
        submit(m.admin(), m.contractId());
        approve(dir, m.contractId());
        // APPROVED
        assertThat(patchTerms(dir, m.contractId(), body(m, "rate", "16000")).status()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("update contract_position set salary = 99 where id = ?", position))
                .hasMessageContaining("cannot be updated or deleted");
    }

    @Test
    void theStartDateMovesOnlyWhenTheChainStaysGaplessAndNobodyWasPlacedBefore() {
        // an earlier MoU ends the day before: refused
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();
        createContract(admin, f.schoolId(), startedDaysAgo(sameSalaryBody(f, dir.userId(), 1, "1000"), 30));
        UUID second = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 2, "2000")));
        Mou chained = new Mou(f, admin, dir, second, null);
        submit(admin, second);
        Resp refused = patchTerms(tokenOf(dir), second, body(chained, "startsOn", today.plusDays(3).toString()));
        assertThat(refused.status()).as(refused.body()).isEqualTo(409);
        assertThat(refused.body()).contains("earlier MoU");

        // a Teacher placed on a position before the new date: refused
        Mou placed = mou();
        UUID teacher = teacher(placed.admin());
        UUID position = UUID.fromString((String) positionsOf(contractOf(placed)).get(0).get("id"));
        assertThat(assign(placed.admin(), teacher, placed.f().schoolId(), position, today).status()).isEqualTo(200);
        submit(placed.admin(), placed.contractId());
        Resp tooLate = patchTerms(tokenOf(placed.director()), placed.contractId(), body(placed, "startsOn", today.plusDays(3).toString()));
        assertThat(tooLate.status()).as(tooLate.body()).isEqualTo(409);

        // neither: accepted
        Mou free = mou();
        submit(free.admin(), free.contractId());
        Resp ok = patchTerms(tokenOf(free.director()), free.contractId(), body(free, "startsOn", today.minusDays(4).toString()));
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(contractOf(free)).containsEntry("startsOn", today.minusDays(4).toString());
    }
}
