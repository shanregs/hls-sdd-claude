package com.hls.expenses.web;

import com.hls.expenses.internal.DuplicateClaimException;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.Reason;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps the shared master-data exceptions to the same status codes and {@code {"reason"}} body for the expense endpoints.
 * Kept inside the expenses module so the module needs no change to the application-wide advice.
 */
@RestControllerAdvice(basePackages = "com.hls.expenses")
public class ExpensesExceptionAdvice {

    private static final String STALE_MESSAGE = "This claim was changed by someone else. Reload and try again.";

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

    /** 409 with the matching claim ids so the client can show them and ask for confirmation (FR-008). */
    @ExceptionHandler(DuplicateClaimException.class)
    ResponseEntity<Map<String, Object>> duplicate(DuplicateClaimException e) {
        return ResponseEntity.status(409).body(Map.of("reason", e.getMessage(), "matches", e.matches()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Reason> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(400).body(new Reason("The file is too large. A bill can be at most 10 MB."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Reason> stale(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(409).body(new Reason(STALE_MESSAGE));
    }
}
