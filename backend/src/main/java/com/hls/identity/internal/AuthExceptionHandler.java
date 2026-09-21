package com.hls.identity.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * FR-015: every "can't authenticate" case maps to the same generic 401 body,
 * regardless of which specific reason ({@link InvalidCredentialsException} or
 * {@link TokenService.InvalidSessionException}) triggered it.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, TokenService.InvalidSessionException.class})
    public ResponseEntity<AuthController.GenericAuthError> handleAuthFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthController.GenericAuthError.DEFAULT);
    }
}
