package com.hls;

import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.Reason;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the master-data exceptions to the contract's status codes and {@code {"reason"}} body. */
@RestControllerAdvice(basePackages = {"com.hls.school", "com.hls.organization", "com.hls.teacher", "com.hls.attendance", "com.hls.leave", "com.hls.notification", "com.hls.schoolbilling"})
public class MasterDataExceptionAdvice {

    static final String STALE_MESSAGE = "This record was changed by someone else. Reload and try again.";

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
