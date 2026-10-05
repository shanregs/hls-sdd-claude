package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MoU and Active are derived from spec 012; the signed MoU may differ from the proposal and both are shown. */
class ContractStatusTest extends WonProspectTestBase {

    private Map<String, Object> status(String token, UUID prospect) {
        @SuppressWarnings("unchecked")
        Map<String, Object> s = (Map<String, Object>) get(M + "/prospects/" + prospect, token).map().get("contractStatus");
        return s;
    }

    private String effective(String token, UUID prospect) {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) get(M + "/prospects/" + prospect, token).map().get("row");
        return (String) row.get("effectiveStage");
    }

    @Test
    void theStageIsWonThenMouThenActiveAsTheContractAndMappingAppear() {
        String admin = admin();
        Won won = wonProspect(admin);
        assertThat(status(admin, won.prospect())).containsEntry("status", "NOT_RECORDED");
        assertThat(effective(admin, won.prospect())).isEqualTo("WON");

        UUID place = place(admin, won.zone());
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, winBody(place)).status()).isEqualTo(200);
        UUID school = schoolOf(admin, won.prospect());
        assertThat(status(admin, won.prospect())).containsEntry("status", "NOT_RECORDED");
        assertThat(effective(admin, won.prospect())).isEqualTo("WON");

        ManagerCtx manager = newManager(admin, won.zone());
        assignSchoolManager(admin, school, manager.managerId());
        Fixture f = fixtureOf(won.zone(), school, manager);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        Map<String, Object> mou = startedDaysAgo(sameSalaryBody(f, dir, 5, "16000"), 3);
        assertThat(createContract(admin, school, mou).status()).isEqualTo(201);

        assertThat(effective(admin, won.prospect())).isEqualTo("MOU");
        Map<String, Object> mouStatus = status(admin, won.prospect());
        assertThat(mouStatus).containsEntry("status", "MOU");
        assertThat(mouStatus.toString()).contains("teacherCount=5", "filled=0", "vacant=5");
        // the signed MoU differs from the proposal (4 Teachers at 15,000): both are returned side by side
        assertThat(mouStatus.get("differences").toString()).contains("Teachers: proposal 4, MoU 5").contains("Salary: proposal 15000.00, MoU 16000.00");
        assertThat(mouStatus.get("proposal")).isNotNull();

        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, school, null, today.minusDays(1)).status()).isEqualTo(200);

        assertThat(effective(admin, won.prospect())).isEqualTo("ACTIVE");
        assertThat(status(admin, won.prospect()).toString()).contains("filled=1", "vacant=4");
        String board = get(M + "/pipeline", admin).body();
        assertThat(board).contains(won.prospect().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) get(M + "/pipeline", admin).map().get("counts");
        assertThat(((Number) counts.get("ACTIVE")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aProspectThatIsNotWonShowsNoContractAndNoContractDataIsStoredHere() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        assertThat(status(admin, prospect)).containsEntry("status", "NOT_WON");
        Integer contractColumns = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'marketing_prospect' and column_name like '%contract%'",
                Integer.class);
        assertThat(contractColumns).isZero();
    }
}
