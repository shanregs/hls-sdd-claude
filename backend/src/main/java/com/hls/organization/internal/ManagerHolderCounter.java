package com.hls.organization.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.designation.api.HolderCounter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tells the designation list how many Managers hold each designation and how many have details missing. */
@Component
@Transactional(readOnly = true)
class ManagerHolderCounter implements HolderCounter {

    private final ManagerEmploymentService employment;

    ManagerHolderCounter(ManagerEmploymentService employment) {
        this.employment = employment;
    }

    @Override
    public Kind kind() {
        return Kind.MANAGER;
    }

    @Override
    public Map<UUID, Long> holdersByDesignation() {
        return employment.holdersToday();
    }

    @Override
    public long missingDesignation() {
        return employment.missing().noDesignation();
    }

    @Override
    public long missingJoiningDate() {
        return employment.missing().noJoiningDate();
    }

    @Override
    public long missingExitDate() {
        return employment.missing().inactiveNoExit();
    }
}
