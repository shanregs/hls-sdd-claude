package com.hls.school.api;

/** The caller may not change one of the fields they submitted. Maps to 403. */
public class ForbiddenFieldException extends RuntimeException {

    public ForbiddenFieldException(String message) {
        super(message);
    }
}
