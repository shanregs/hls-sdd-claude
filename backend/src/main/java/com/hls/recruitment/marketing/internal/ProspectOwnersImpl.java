package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.ProspectOwners;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who owns the prospect that won a School, for the incentive spec (030). Pays nothing itself. */
@Service
class ProspectOwnersImpl implements ProspectOwners {

    private final ProspectRepository prospects;

    ProspectOwnersImpl(ProspectRepository prospects) {
        this.prospects = prospects;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> ownerOfSchool(UUID schoolId) {
        return prospects.findFirstBySchoolId(schoolId).filter(Prospect::isWon).map(Prospect::getOwnerUserId);
    }
}
