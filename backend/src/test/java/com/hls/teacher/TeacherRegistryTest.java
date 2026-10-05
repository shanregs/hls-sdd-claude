package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.support.MasterDataTestBase;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 016: the interface through which recruitment creates and moves Teachers without touching teacher internals. */
class TeacherRegistryTest extends MasterDataTestBase {

    @Autowired
    TeacherRegistry registry;

    private UUID actor() {
        return signInAs(Role.ADMIN).userId();
    }

    private static String phone() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L);
    }

    @Test
    void createTraineeMakesAnInTrainingTeacherWithNoSalaryEntry() {
        UUID id = registry.createTrainee(actor(), new TeacherRegistry.Candidate("Asha K", phone(), "asha@x.test", "Chennai"));

        assertThat(registry.hasSalaryEntry(id)).isFalse();
        assertThat(registry.findMatches(null, "ASHA@x.test")).extracting(TeacherRegistry.Match::status)
                .containsExactly("IN_TRAINING");
        assertThat(registry.activeTeacherIds()).doesNotContain(id);
    }

    @Test
    void findMatchesNormalizesThePhoneAndLowerCasesTheEmail() {
        String digits = phone();
        UUID id = registry.createTrainee(actor(), new TeacherRegistry.Candidate("Ravi", "+91 " + digits.substring(0, 5) + "-" + digits.substring(5), null, null));

        assertThat(registry.findMatches(digits, null)).extracting(TeacherRegistry.Match::teacherId).containsExactly(id);
        assertThat(registry.findMatches("0091" + digits, null)).extracting(TeacherRegistry.Match::teacherId).containsExactly(id);
        assertThat(registry.findMatches("9000000000" , "nobody@nowhere.test")).isEmpty();
        assertThat(registry.findMatches(null, null)).isEmpty();
    }

    @Test
    void activateMovesOnlyAnInTrainingTeacherToActive() {
        UUID actor = actor();
        UUID id = registry.createTrainee(actor, new TeacherRegistry.Candidate("Meena", phone(), null, null));

        registry.activate(actor, id);

        assertThat(registry.activeTeacherIds()).contains(id);
        assertThatThrownBy(() -> registry.activate(actor, id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void exitFollowsTheStatusMachineAndTakesTheTeacherOutOfTheActiveSet() {
        UUID actor = actor();
        UUID id = registry.createTrainee(actor, new TeacherRegistry.Candidate("Kumar", phone(), null, null));
        registry.activate(actor, id);

        registry.exit(actor, id, LocalDate.now(), "Left the programme");

        assertThat(registry.activeTeacherIds()).doesNotContain(id);
        assertThatThrownBy(() -> registry.activate(actor, id)).isInstanceOf(ConflictException.class);
        assertThat(registry.findMatches(null, null)).isEmpty();
    }

    @Test
    void recordFirstSalaryWritesOneEntryAndRefusesASecond() {
        UUID actor = actor();
        UUID id = registry.createTrainee(actor, new TeacherRegistry.Candidate("Divya", phone(), null, null));
        LocalDate start = LocalDate.now().minusDays(3);

        registry.recordFirstSalary(actor, id, new BigDecimal("18000"), start);

        assertThat(registry.hasSalaryEntry(id)).isTrue();
        assertThatThrownBy(() -> registry.recordFirstSalary(actor, id, new BigDecimal("19000"), start))
                .isInstanceOf(ConflictException.class);
        Integer rows = jdbc.queryForObject(
                "select count(*) from teacher_salary_history where teacher_id = ?", Integer.class, id);
        assertThat(rows).isEqualTo(1);
        BigDecimal amount = jdbc.queryForObject(
                "select amount from teacher_salary_history where teacher_id = ?", BigDecimal.class, id);
        assertThat(amount).isEqualByComparingTo("18000.00");
    }

    @Test
    void findMatchesReportsEveryTeacherThatSharesThePhoneOrEmail() {
        UUID actor = actor();
        String shared = phone();
        UUID a = registry.createTrainee(actor, new TeacherRegistry.Candidate("One", shared, null, null));
        UUID b = registry.createTrainee(actor, new TeacherRegistry.Candidate("Two", "000" + shared, "two-" + shared + "@x.test", null));

        List<UUID> ids = registry.findMatches(shared, "two-" + shared + "@x.test").stream()
                .map(TeacherRegistry.Match::teacherId)
                .toList();

        assertThat(ids).containsExactlyInAnyOrder(a, b);
    }
}
