package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proposal revisions: salary modes, validation, immutability (service and trigger), labelling and scope. */
class ProposalApiTest extends MarketingTestBase {

    private Map<String, Object> same(int count, String rate) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherCount", count);
        body.put("startMonth", "2026-12");
        body.put("salaryMode", "SAME_FOR_ALL");
        body.put("rate", rate);
        return body;
    }

    private Map<String, Object> perTeacher(String... salaries) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherCount", salaries.length);
        body.put("startMonth", "2027-01-15");
        body.put("salaryMode", "PER_TEACHER");
        body.put("positions", java.util.Arrays.stream(salaries).map(s -> Map.of("title", "Teacher", "salary", s)).toList());
        return body;
    }

    @Test
    void aSameSalaryProposalComputesTheMonthlyTotalAndIsLabelledNotAContract() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        Resp created = post(M + "/prospects/" + prospect + "/proposals", admin, same(4, "15000"));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.map()).containsEntry("revision", 1).containsEntry("label", "Proposal (not a contract)")
                .containsEntry("monthlyTotal", "60000.00").containsEntry("rate", "15000.00");
        assertThat(created.map().get("startMonth")).isEqualTo("2026-12-01");
    }

    @Test
    void aRevisionIsNewAndTheOldOneStaysUnchangedWithTheLatestFirst() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        post(M + "/prospects/" + prospect + "/proposals", admin, same(4, "15000"));

        Resp second = post(M + "/prospects/" + prospect + "/proposals", admin, perTeacher("14000", "15000", "16000", "16500", "17000"));

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.map()).containsEntry("revision", 2).containsEntry("monthlyTotal", "78500.00").containsEntry("teacherCount", 5);
        Resp list = get(M + "/prospects/" + prospect + "/proposals", admin);
        assertThat(list.body().indexOf("\"revision\":2")).isLessThan(list.body().indexOf("\"revision\":1"));
        assertThat(list.body()).contains("60000.00", "78500.00");
    }

    @Test
    void validationRefusesBadCountsAmountsAndModes() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        String url = M + "/prospects/" + prospect + "/proposals";

        assertThat(post(url, admin, same(0, "15000")).status()).isEqualTo(400);
        assertThat(post(url, admin, same(501, "15000")).status()).isEqualTo(400);
        assertThat(post(url, admin, same(4, "0")).status()).isEqualTo(400);
        assertThat(post(url, admin, same(4, "15000.123")).status()).isEqualTo(400);
        assertThat(post(url, admin, same(4, "abc")).status()).isEqualTo(400);
        Map<String, Object> sameWithPositions = same(2, "15000");
        sameWithPositions.put("positions", List.of(Map.of("salary", "1")));
        assertThat(post(url, admin, sameWithPositions).status()).isEqualTo(400);
        Map<String, Object> wrongNumber = perTeacher("14000", "15000");
        wrongNumber.put("teacherCount", 3);
        assertThat(post(url, admin, wrongNumber).status()).isEqualTo(400);
        assertThat(post(url, admin, perTeacher("14000", "-5")).status()).isEqualTo(400);
        Map<String, Object> badMode = same(2, "15000");
        badMode.put("salaryMode", "MIXED");
        assertThat(post(url, admin, badMode).status()).isEqualTo(400);
        Map<String, Object> noMonth = same(2, "15000");
        noMonth.remove("startMonth");
        assertThat(post(url, admin, noMonth).status()).isEqualTo(400);
        assertThat(get(url, admin).body()).isEqualTo("[]");
    }

    @Test
    void aRevisionCanNeverBeUpdatedOrDeletedBySql() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID revision = post(M + "/prospects/" + prospect + "/proposals", admin, perTeacher("14000", "15000")).id();

        assertThatThrownBy(() -> jdbc.update("update proposal_revision set teacher_count = 9 where id = ?", revision)).hasMessageContaining("never changed");
        assertThatThrownBy(() -> jdbc.update("delete from proposal_revision where id = ?", revision)).hasMessageContaining("never changed");
        assertThatThrownBy(() -> jdbc.update("update proposal_position set salary = 1 where revision_id = ?", revision)).hasMessageContaining("never changed");
        assertThatThrownBy(() -> jdbc.update("delete from proposal_position where revision_id = ?", revision)).hasMessageContaining("never changed");
    }

    @Test
    void theRoleAndZoneMatrixAppliesToProposals() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID inA = prospect(admin, zoneA);
        UUID inB = prospect(admin, zoneB);

        assertThat(post(M + "/prospects/" + inA + "/proposals", managerA.token(), same(2, "15000")).status()).isEqualTo(201);
        assertThat(get(M + "/prospects/" + inA + "/proposals", managerA.token()).status()).isEqualTo(200);
        assertThat(post(M + "/prospects/" + inB + "/proposals", managerA.token(), same(2, "15000")).status()).isEqualTo(404);
        assertThat(get(M + "/prospects/" + inB + "/proposals", managerA.token()).status()).isEqualTo(404);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(M + "/prospects/" + inA + "/proposals", signInAs(role).token()).status()).isEqualTo(403);
        }
        assertThat(get(M + "/prospects/" + inA + "/proposals", null).status()).isEqualTo(401);
        assertChangeRecorded(admin, "PROPOSAL", inA, "revision 1");
    }
}
