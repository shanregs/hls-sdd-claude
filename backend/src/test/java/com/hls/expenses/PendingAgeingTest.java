package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 015 US6 (FR-023): pending claims by age since submission, with the boundaries between the four buckets. */
class PendingAgeingTest extends ExpensesTestBase {

    @SuppressWarnings("unchecked")
    private long[] counts(String token) {
        Resp resp = get(REVIEW + "/pending", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) resp.map().get("buckets");
        long[] out = new long[4];
        for (int i = 0; i < 4; i++) {
            out[i] = ((Number) buckets.get(i).get("count")).longValue();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal total(String token, int index) {
        Resp resp = get(REVIEW + "/pending", token);
        return new BigDecimal((String) ((List<Map<String, Object>>) resp.map().get("buckets")).get(index).get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bucketsAreOldestFirstAndCoverEveryAgeWithBoundaries() {
        String admin = admin().token();
        String claimant = signInAs(Role.MANAGER).token();
        long[] before = counts(admin);
        BigDecimal oldestTotalBefore = total(admin, 0);

        int[] ages = {0, 2, 3, 7, 8, 14, 15};
        for (int i = 0; i < ages.length; i++) {
            UUID id = newSubmittedClaim(claimant, (101 + i) + ".00");
            setSubmittedAt(id, noonOn(today().minusDays(ages[i])));
        }

        long[] after = counts(admin);
        // order: over 14, 8-14, 3-7, 0-2
        assertThat(after[0] - before[0]).isEqualTo(1); // 15
        assertThat(after[1] - before[1]).isEqualTo(2); // 8, 14
        assertThat(after[2] - before[2]).isEqualTo(2); // 3, 7
        assertThat(after[3] - before[3]).isEqualTo(2); // 0, 2
        assertThat(total(admin, 0).subtract(oldestTotalBefore)).isEqualByComparingTo("107.00");

        Resp resp = get(REVIEW + "/pending", admin);
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) resp.map().get("buckets");
        assertThat(buckets).extracting(b -> b.get("label")).containsExactly("Over 14 days", "8-14 days", "3-7 days", "0-2 days");
        assertThat(resp.map().get("oldestSubmittedAt")).isNotNull();
    }

    @Test
    void aResubmissionRestartsTheAgeAndADecidedClaimLeavesTheCount() {
        String admin = admin().token();
        String claimant = signInAs(Role.MANAGER).token();
        UUID id = newSubmittedClaim(claimant, "55.00");
        setSubmittedAt(id, noonOn(today().minusDays(20)));
        long[] old = counts(admin);

        assertThat(reject(admin, id, "Fix the bill").status()).isEqualTo(200);
        long[] afterReject = counts(admin);
        assertThat(old[0] - afterReject[0]).isEqualTo(1);

        assertThat(submit(claimant, id).status()).isEqualTo(200);
        long[] afterResubmit = counts(admin);
        assertThat(afterResubmit[3] - afterReject[3]).isEqualTo(1);
        assertThat(afterResubmit[0]).isEqualTo(afterReject[0]);
    }

    @Test
    void managerIsRefused() {
        assertThat(get(REVIEW + "/pending", signInAs(Role.MANAGER).token()).status()).isEqualTo(403);
    }
}
