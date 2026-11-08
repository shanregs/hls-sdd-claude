package com.hls.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.reporting.internal.SameDayReportService;
import com.hls.reporting.web.SameDayReportController;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Regression: with no month given, the same-day report opens on the IST month, even in the first hours of it. */
class SameDayReportDefaultMonthTest {

    @Test
    void defaultMonthIsTheIstMonthJustAfterIstMidnightOnTheFirst() {
        // 2026-10-31T19:00Z is 2026-11-01 00:30 IST.
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T19:00:00Z"), ZoneOffset.UTC);
        SameDayReportService service = mock(SameDayReportService.class);
        when(service.report(any(), any(), any(), any())).thenReturn(Map.of());
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN"))
                .build();

        new SameDayReportController(service, new BusinessDate(clock, "Asia/Kolkata")).report(null, null, jwt);

        verify(service).report(any(), any(), org.mockito.ArgumentMatchers.eq(YearMonth.of(2026, 11)), any());
    }
}
