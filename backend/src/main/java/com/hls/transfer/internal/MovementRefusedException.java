package com.hls.transfer.internal;

import java.util.List;

/** A rule refused the request; nothing was changed. Carries the plain-words problems. Maps to 409. */
public class MovementRefusedException extends RuntimeException {

    private final transient List<String> problems;

    public MovementRefusedException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
