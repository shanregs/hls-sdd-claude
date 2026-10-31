package com.hls.payroll.run.internal;

/**
 * The status derivation shared by the reimbursement intake (spec 015) and the incentive intake (spec 030): what an
 * adjustment that came from another module is doing, read from its own status and the run that holds it. Each intake
 * has its own enum with the same constant names.
 */
final class IntakeStatus {

    private IntakeStatus() {}

    static <E extends Enum<E>> E of(PayAdjustment adjustment, PayRunRepository runs, Class<E> type) {
        return Enum.valueOf(type, nameOf(adjustment, runs));
    }

    private static String nameOf(PayAdjustment adjustment, PayRunRepository runs) {
        return switch (adjustment.getStatus()) {
            case APPLIED -> "APPLIED";
            case WITHDRAWN -> "WITHDRAWN";
            case WAITING -> {
                if (adjustment.getAttachedRunId() == null) {
                    yield "WAITING";
                }
                yield runs.findById(adjustment.getAttachedRunId())
                        .map(run -> switch (run.getStatus()) {
                            case DRAFT -> "HELD_BY_DRAFT";
                            case AWAITING_APPROVAL -> "HELD_AWAITING_APPROVAL";
                            default -> "WAITING";
                        })
                        .orElse("WAITING");
            }
        };
    }
}
