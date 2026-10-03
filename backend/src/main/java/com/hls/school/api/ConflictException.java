package com.hls.school.api;

/** A business rule refused the change; nothing was changed. Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
