package com.hls.transfer.web;

import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.Reason;
import com.hls.transfer.internal.MovementRefusedException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the shared exceptions to the status codes and {"reason"} body of the other modules, plus {@code problems} for a rule
 * refusal (spec 027 contracts/transfer-api.md). Kept inside the module so the application-wide advice is unchanged.
 */
@RestControllerAdvice(basePackages = "com.hls.transfer")
public class TransferExceptionAdvice {

    private static final String STALE_MESSAGE = "This request changed. Reload and try again.";

    /** The error body: {@code reason} always, {@code problems} when a rule refused the change. */
    public record Problem(String reason, List<String> problems) {}

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Reason> notFound(NotFoundException e) {
        return ResponseEntity.status(404).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Problem> conflict(ConflictException e) {
        return ResponseEntity.status(409).body(new Problem(e.getMessage(), List.of(e.getMessage())));
    }

    @ExceptionHandler(MovementRefusedException.class)
    ResponseEntity<Problem> refused(MovementRefusedException e) {
        return ResponseEntity.status(409).body(new Problem(e.getMessage(), e.problems()));
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
