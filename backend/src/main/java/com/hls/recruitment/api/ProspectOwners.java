package com.hls.recruitment.api;

import java.util.Optional;
import java.util.UUID;

/** Who won a School, for the incentive spec (030); spec 023 pays nothing itself. */
public interface ProspectOwners {

    /** The owner of the won prospect linked to the School, if any. */
    Optional<UUID> ownerOfSchool(UUID schoolId);
}
