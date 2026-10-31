package com.hls.schoolreports.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Splits one payslip net across the Schools a Teacher was billed at, in proportion to the working days each School billed
 * (spec 014b research D1). Each part is rounded half-even to the paisa and the remainder paisa goes to the School with the most
 * days (ties: the lower School id), so the parts always add up to the net exactly.
 */
final class PaySplit {

    private static final int SCALE = 2;

    private PaySplit() {}

    static Map<UUID, BigDecimal> split(BigDecimal net, Map<UUID, BigDecimal> daysBySchool) {
        BigDecimal total = daysBySchool.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (daysBySchool.isEmpty() || total.signum() <= 0) {
            throw new IllegalArgumentException("A pay split needs at least one billed day.");
        }
        BigDecimal exact = net.setScale(SCALE, RoundingMode.HALF_EVEN);
        Map<UUID, BigDecimal> parts = new LinkedHashMap<>();
        BigDecimal given = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> e : daysBySchool.entrySet()) {
            BigDecimal part = exact.multiply(e.getValue()).divide(total, SCALE, RoundingMode.HALF_EVEN);
            parts.put(e.getKey(), part);
            given = given.add(part);
        }
        BigDecimal remainder = exact.subtract(given);
        if (remainder.signum() != 0) {
            UUID heaviest = daysBySchool.entrySet().stream()
                    .max(Comparator.<Map.Entry<UUID, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .orElseThrow()
                    .getKey();
            parts.merge(heaviest, remainder, BigDecimal::add);
        }
        return parts;
    }
}
