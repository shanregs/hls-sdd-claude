package com.hls.school.api;

/** The request is malformed or breaks a field rule. Maps to 400. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
