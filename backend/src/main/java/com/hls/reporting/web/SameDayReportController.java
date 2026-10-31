package com.hls.reporting.web;

import com.hls.reporting.internal.DashboardInvalidInputException;
import com.hls.reporting.internal.SameDayReportService;
import com.hls.school.api.CallerContext;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The same-day report (spec 040 contracts, section 5): read-only; the sections are the ones the caller may view. */
@RestController
@RequestMapping("/api/v1/reports/same-day")
public class SameDayReportController {

    private final SameDayReportService service;

    public SameDayReportController(SameDayReportService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, SameDayReportService.Section> report(
            @RequestParam(required = false) String month, @AuthenticationPrincipal Jwt jwt) {
        YearMonth wanted;
        try {
            wanted = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new DashboardInvalidInputException("The month must look like 2026-10.");
        }
        return service.report(CallerContext.userId(jwt), CallerContext.roles(jwt), wanted);
    }
}
