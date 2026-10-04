package com.hls.attendance.web;

import com.hls.attendance.internal.MonthLockService;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A refused lock lists who and which days are unmarked, so it can be fixed (spec 008 FR-016). */
@RestControllerAdvice(basePackages = "com.hls.attendance")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttendanceExceptionAdvice {

    @ExceptionHandler(MonthLockService.UnmarkedDaysException.class)
    ResponseEntity<Map<String, Object>> unmarked(MonthLockService.UnmarkedDaysException e) {
        return ResponseEntity.status(409).body(Map.of("reason", e.getMessage(), "unmarked", e.unmarked()));
    }
}
