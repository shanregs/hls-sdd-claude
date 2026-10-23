package com.hls.reporting.web;

import com.hls.reporting.internal.DashboardForbiddenException;
import com.hls.reporting.internal.DashboardInvalidInputException;
import com.hls.reporting.internal.DashboardNotFoundException;
import com.hls.school.api.Reason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the Dashboard's exceptions to the same status codes and {@code {"reason"}} body the other modules use. */
@RestControllerAdvice(basePackages = "com.hls.reporting")
public class DashboardExceptionAdvice {

    @ExceptionHandler(DashboardNotFoundException.class)
    ResponseEntity<Reason> notFound(DashboardNotFoundException e) {
        return ResponseEntity.status(404).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(DashboardForbiddenException.class)
    ResponseEntity<Reason> forbidden(DashboardForbiddenException e) {
        return ResponseEntity.status(403).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(DashboardInvalidInputException.class)
    ResponseEntity<Reason> invalid(DashboardInvalidInputException e) {
        return ResponseEntity.status(400).body(new Reason(e.getMessage()));
    }
}
