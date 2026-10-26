package com.hls.training.calendar.web;

import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.Reason;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the shared exceptions to the status codes and {"reason"} body of the other modules (spec 024 contracts/training-api.md):
 * 400 invalid input, 403 no grant, 404 out of scope or unknown, 409 conflict or a stale version. Kept inside the module so
 * the application-wide advice is unchanged.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hls.training.calendar")
public class TrainingExceptionAdvice {

    private static final String STALE_MESSAGE = "This session changed. Reload and try again.";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Reason> notFound(NotFoundException e) {
        return ResponseEntity.status(404).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Reason> conflict(ConflictException e) {
        return ResponseEntity.status(409).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<Reason> invalid(InvalidInputException e) {
        return ResponseEntity.status(400).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenFieldException.class)
    ResponseEntity<Reason> forbidden(ForbiddenFieldException e) {
        return ResponseEntity.status(403).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Reason> stale(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(409).body(new Reason(STALE_MESSAGE));
    }
}
