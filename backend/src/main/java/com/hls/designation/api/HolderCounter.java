package com.hls.designation.api;

import java.util.Map;
import java.util.UUID;

/**
 * Implemented by the modules that own people ({@code organization} for Managers, {@code teacher} for Teachers) so
 * the designation list can show how many people hold each designation and who is missing details, without the
 * designation module depending on them.
 */
public interface HolderCounter {

    DesignationDirectory.Kind kind();

    /** People whose current designation is the key (absent key means none). */
    Map<UUID, Long> holdersByDesignation();

    long missingDesignation();

    /** Managers without a joining date; 0 for Teachers. */
    long missingJoiningDate();

    /** Inactive Managers without an exit date; 0 for Teachers. */
    long missingExitDate();
}
