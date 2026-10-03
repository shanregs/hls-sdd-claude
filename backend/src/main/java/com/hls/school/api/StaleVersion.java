package com.hls.school.api;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Compares the version a client loaded with the current one (FR-026). */
public final class StaleVersion {

    private StaleVersion() {}

    public static void check(Class<?> entityType, Object id, Long currentVersion, Long requestedVersion) {
        if (requestedVersion == null) {
            throw new InvalidInputException("The record version is required.");
        }
        if (!requestedVersion.equals(currentVersion)) {
            throw new ObjectOptimisticLockingFailureException(entityType, id);
        }
    }
}
