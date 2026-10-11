package com.hls.payroll.api;

import java.util.List;

/** The payable dates a person was unpaid on, or the stated reason this cannot be known. Never an empty guess. */
public sealed interface UnpaidDays {

    record Found(List<UnpaidDay> days) implements UnpaidDays {}

    /** For example a Manager, whose attendance is not recorded yet (spec 032). Callers must not read this as zero. */
    record Unavailable(String reason) implements UnpaidDays {}
}
