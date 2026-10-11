package com.hls.payroll.internal;

import java.time.Instant;
import java.time.LocalDate;

/** A row of an append-only history: it applies from {@code effectiveOn}, and the one recorded last wins a tie. */
public interface Dated {

    LocalDate effectiveOn();

    Instant recordedAt();
}
