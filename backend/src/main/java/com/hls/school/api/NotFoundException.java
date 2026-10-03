package com.hls.school.api;

/** The record does not exist, or is outside the caller's data scope (indistinguishable, FR-020). Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
