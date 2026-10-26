package com.hls.schoolbilling.web;

import com.hls.schoolbilling.internal.CollectionService.LikelyDuplicateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A likely duplicate collection is a 409 carrying {@code warning}, so the screen can offer to continue (spec 022 FR-012). */
@RestControllerAdvice(basePackages = "com.hls.schoolbilling")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BillingExceptionAdvice {

    public record Warning(String reason, String warning) {}

    @ExceptionHandler(LikelyDuplicateException.class)
    ResponseEntity<Warning> duplicate(LikelyDuplicateException e) {
        return ResponseEntity.status(409).body(new Warning(e.getMessage(), "LIKELY_DUPLICATE"));
    }
}
