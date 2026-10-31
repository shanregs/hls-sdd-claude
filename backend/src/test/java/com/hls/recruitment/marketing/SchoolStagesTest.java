package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.SchoolStages;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014b A5: the stage per School equals the stage the pipeline shows for the linked prospect. */
class SchoolStagesTest extends WonProspectTestBase {

    @Autowired
    private SchoolStages stages;

    @SuppressWarnings("unchecked")
    private String effective(String token, UUID prospect) {
        Map<String, Object> row = (Map<String, Object>) get(M + "/prospects/" + prospect, token).map().get("row");
        return (String) row.get("effectiveStage");
    }

    @Test
    void theStageFollowsWonThenMouThenActiveLikeThePipeline() {
        String admin = admin();
        Won won = wonProspect(admin);
        UUID place = place(admin, won.zone());
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, winBody(place)).status()).isEqualTo(200);
        UUID school = schoolOf(admin, won.prospect());

        assertThat(stages.stageOf(Set.of(school))).containsEntry(school, "WON").containsEntry(school, effective(admin, won.prospect()));

        ManagerCtx manager = newManager(admin, won.zone());
        assignSchoolManager(admin, school, manager.managerId());
        Fixture f = fixtureOf(won.zone(), school, manager);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        assertThat(createContract(admin, school, startedDaysAgo(sameSalaryBody(f, dir, 5, "16000"), 3)).status()).isEqualTo(201);
        assertThat(stages.stageOf(Set.of(school)).get(school)).isEqualTo("MOU").isEqualTo(effective(admin, won.prospect()));

        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, school, null, today.minusDays(1)).status()).isEqualTo(200);
        assertThat(stages.stageOf(Set.of(school)).get(school)).isEqualTo("ACTIVE").isEqualTo(effective(admin, won.prospect()));
    }

    @Test
    void aSchoolWithoutAProspectIsAbsentAndAnEmptySetGivesNothing() {
        String admin = admin();
        Fixture plain = fixture(admin);

        assertThat(stages.stageOf(Set.of(plain.schoolId()))).doesNotContainKey(plain.schoolId());
        assertThat(stages.stageOf(Set.of())).isEmpty();
    }
}
