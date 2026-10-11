package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules;
import com.hls.support.PayrollTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 013a US2 (T022). The policy is shared by the whole test database, so every test records its versions in a year
 * of its own, far from any date another test reads, and asserts only on what it recorded.
 */
class PayPolicyApiTest extends PayrollTestBase {

    @Autowired
    private PayRules payRules;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(Resp policy) {
        return (List<Map<String, Object>>) policy.map().get("history");
    }

    @Test
    void aNewInstallationReturnsTheStandardRules() {
        String admin = signInAs(Role.ADMIN).token();
        Resp policy = get(BASE + "/policy", admin);
        assertThat(policy.status()).isEqualTo(200);
        Map<String, Object> seeded = history(policy).stream()
                .filter(v -> "2000-01-01".equals(v.get("effectiveOn")))
                .findFirst()
                .orElseThrow();
        assertThat(seeded)
                .containsEntry("lopDivisor", "WORKING_DAYS")
                .containsEntry("halfDayFraction", "0.500")
                .containsEntry("rounding", "NEAREST_RUPEE")
                .containsEntry("payMonth", "CALENDAR_MONTH");
        assertThat(seeded.get("recordedBy")).isNull();
        assertThat(payRules.policyOn(LocalDate.of(2026, 10, 11)).halfDayFraction()).isEqualByComparingTo("0.5");
    }

    @Test
    void aNewVersionIsAddedAndTheOldOneIsUnchangedAndEachDateUsesItsOwnVersion() {
        String admin = signInAs(Role.ADMIN).token();
        Map<String, Object> seededBefore = history(get(BASE + "/policy", admin)).stream()
                .filter(v -> "2000-01-01".equals(v.get("effectiveOn")))
                .findFirst()
                .orElseThrow();

        assertThat(recordPolicy(admin, "2091-03-01", "0.400", "UP").status()).isEqualTo(201);
        assertThat(recordPolicy(admin, "2091-06-01", "0.600", "DOWN").status()).isEqualTo(201);

        List<Map<String, Object>> history = history(get(BASE + "/policy", admin));
        assertThat(history).contains(seededBefore);
        assertThat(history.stream().map(v -> v.get("effectiveOn")).toList()).contains("2091-06-01", "2091-03-01");

        assertThat(payRules.policyOn(LocalDate.of(2091, 2, 28)).rounding()).isEqualTo(PayPolicyView.RoundingRule.NEAREST_RUPEE);
        PayPolicyView march = payRules.policyOn(LocalDate.of(2091, 3, 1));
        assertThat(march.rounding()).isEqualTo(PayPolicyView.RoundingRule.UP);
        assertThat(march.halfDayFraction()).isEqualByComparingTo("0.4");
        assertThat(payRules.policyOn(LocalDate.of(2091, 5, 31)).rounding()).isEqualTo(PayPolicyView.RoundingRule.UP);
        assertThat(payRules.policyOn(LocalDate.of(2091, 6, 1)).rounding()).isEqualTo(PayPolicyView.RoundingRule.DOWN);
        assertThat(payRules.policyOn(LocalDate.of(2091, 12, 31)).halfDayFraction()).isEqualByComparingTo("0.6");
    }

    @Test
    void twoVersionsOnOneDateGoToTheOneRecordedLast() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(recordPolicy(admin, "2092-01-01", "0.300", "UP").status()).isEqualTo(201);
        assertThat(recordPolicy(admin, "2092-01-01", "0.700", "DOWN").status()).isEqualTo(201);
        PayPolicyView view = payRules.policyOn(LocalDate.of(2092, 1, 15));
        assertThat(view.halfDayFraction()).isEqualByComparingTo("0.7");
        assertThat(view.rounding()).isEqualTo(PayPolicyView.RoundingRule.DOWN);
    }

    @Test
    void everyBadVersionIsRefusedWithTheProblemNamed() {
        String admin = signInAs(Role.ADMIN).token();
        String ok = "2093-01-01";
        assertRefused(recordPolicy(admin, ok, "0", "UP"), "more than 0 and less than 1");
        assertRefused(recordPolicy(admin, ok, "1", "UP"), "more than 0 and less than 1");
        assertRefused(recordPolicy(admin, ok, "-0.5", "UP"), "more than 0 and less than 1");
        assertRefused(recordPolicy(admin, ok, "1.5", "UP"), "more than 0 and less than 1");
        assertRefused(recordPolicy(admin, ok, "0.5555", "UP"), "three decimal places");
        assertRefused(recordPolicy(admin, ok, null, "UP"), "half-day fraction is required");
        assertRefused(recordPolicy(admin, ok, "0.5", "SIDEWAYS"), "Unknown rounding rule");
        assertRefused(recordPolicy(admin, ok, "0.5", null), "rounding rule is required");
        assertRefused(recordPolicy(admin, ok, "0.5", "UP", "CALENDAR_DAYS", "CALENDAR_MONTH", null), "Unknown loss-of-pay divisor");
        assertRefused(recordPolicy(admin, ok, "0.5", "UP", "WORKING_DAYS", "FISCAL_MONTH", null), "Unknown pay month");
        assertRefused(recordPolicy(admin, null, "0.5", "UP"), "effective date is required");
        assertRefused(recordPolicy(admin, firstOfMonth().minusDays(1), "0.5", "UP"), "before the first day");
        assertRefused(recordPolicy(admin, ok, "0.5", "UP", "WORKING_DAYS", "CALENDAR_MONTH", "n".repeat(501)), "500 characters");
        // none of them stored a version
        assertThat(history(get(BASE + "/policy", admin)).stream().map(v -> v.get("effectiveOn")))
                .doesNotContain("2093-01-01");
    }

    @Test
    void theBoundariesAreAccepted() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(recordPolicy(admin, "2094-01-01", "0.001", "UP").status()).isEqualTo(201);
        assertThat(recordPolicy(admin, "2094-01-02", "0.999", "UP").status()).isEqualTo(201);
        assertThat(recordPolicy(admin, firstOfMonth().plusYears(70), "0.5", "UP", "WORKING_DAYS", "CALENDAR_MONTH", "n".repeat(500)).status())
                .isEqualTo(201);
    }

    @Test
    void aVersionThatChangesNothingIsAccepted() {
        String admin = signInAs(Role.ADMIN).token();
        Resp same = recordPolicy(admin, "2095-01-01", "0.500", "NEAREST_RUPEE");
        assertThat(same.status()).as(same.body()).isEqualTo(201);
        assertThat(same.map()).containsEntry("effectiveOn", "2095-01-01");
    }

    private void assertRefused(Resp resp, String problem) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(400);
        assertThat(resp.body()).contains(problem);
    }
}
