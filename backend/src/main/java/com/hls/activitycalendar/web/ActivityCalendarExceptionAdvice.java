package com.hls.activitycalendar.web;

import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.Reason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The same status codes and {@code {"reason"}} body as the other modules, kept inside this module. */
@RestControllerAdvice(basePackages = "com.hls.activitycalendar")
public class ActivityCalendarExceptionAdvice {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Reason> notFound(NotFoundException e) {
        return ResponseEntity.status(404).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<Reason> invalid(InvalidInputException e) {
        return ResponseEntity.status(400).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenFieldException.class)
    ResponseEntity<Reason> forbidden(ForbiddenFieldException e) {
        return ResponseEntity.status(403).body(new Reason(e.getMessage()));
    }
}
