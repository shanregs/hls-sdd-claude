package com.hls.schoolreports.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 014b D1 and D9: the pay split is proportional to billed days and always adds up to the net exactly. */
class PaySplitTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static Map<UUID, BigDecimal> days(Object... pairs) {
        Map<UUID, BigDecimal> days = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            days.put((UUID) pairs[i], new BigDecimal(String.valueOf(pairs[i + 1])));
        }
        return days;
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> parts) {
        return parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void oneSchoolGetsTheWholeNet() {
        Map<UUID, BigDecimal> parts = PaySplit.split(new BigDecimal("14000.00"), days(A, 25));

        assertThat(parts).containsOnlyKeys(A);
        assertThat(parts.get(A)).isEqualByComparingTo("14000.00");
    }

    @Test
    void tenAndTwentyDaysSplitOneThirdAndTwoThirds() {
        Map<UUID, BigDecimal> parts = PaySplit.split(new BigDecimal("15000.00"), days(A, 10, B, 20));

        assertThat(parts.get(A)).isEqualByComparingTo("5000.00");
        assertThat(parts.get(B)).isEqualByComparingTo("10000.00");
    }

    @Test
    void anOddPaisaGoesToTheSchoolWithTheMostDaysSoThePartsAddUpExactly() {
        BigDecimal net = new BigDecimal("10000.01");
        Map<UUID, BigDecimal> parts = PaySplit.split(net, days(A, 10, B, 10, C, 10));

        assertThat(sum(parts)).isEqualByComparingTo(net);
        // each third rounds up to 3333.34 (10000.02 in all), so the tie goes to the lower School id, which gives one paisa back
        assertThat(parts.get(A)).isEqualByComparingTo("3333.33");
        assertThat(parts.get(B)).isEqualByComparingTo("3333.34");
        assertThat(parts.get(C)).isEqualByComparingTo("3333.34");
    }

    @Test
    void theRemainderGoesToTheHeaviestSchool() {
        BigDecimal net = new BigDecimal("1000.00");
        Map<UUID, BigDecimal> parts = PaySplit.split(net, days(A, 7, B, 16));

        assertThat(sum(parts)).isEqualByComparingTo(net);
        assertThat(parts.get(A)).isEqualByComparingTo("304.35");
        assertThat(parts.get(B)).isEqualByComparingTo("695.65");
    }

    @Test
    void aTieGoesToTheLowerSchoolId() {
        Map<UUID, BigDecimal> parts = PaySplit.split(new BigDecimal("0.01"), days(B, 5, A, 5));

        assertThat(parts.get(A)).isEqualByComparingTo("0.01");
        assertThat(parts.get(B)).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroBilledDaysIsRefused() {
        assertThatThrownBy(() -> PaySplit.split(new BigDecimal("100"), days(A, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaySplit.split(new BigDecimal("100"), Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
