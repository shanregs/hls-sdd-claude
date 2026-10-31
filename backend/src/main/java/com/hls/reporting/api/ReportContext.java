package com.hls.reporting.api;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Everything a report source needs for one caller and one request; the scope and the Zone and School filters are already checked. */
public interface ReportContext {

    UUID userId();

    Set<Role> roles();

    LocalDate today();

    ReportScope scope();

    ReportRequest request();

    default YearMonth month() {
        return request().month();
    }

    /** Computes once per request and shares the value between the sections of that request. */
    <T> T memo(String key, Supplier<T> compute);
}
