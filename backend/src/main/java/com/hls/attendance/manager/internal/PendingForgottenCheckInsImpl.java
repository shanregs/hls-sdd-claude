package com.hls.attendance.manager.internal;

import com.hls.attendance.api.PendingForgottenCheckIns;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PendingForgottenCheckInsImpl implements PendingForgottenCheckIns {

    private final ManagerRegularizationRepository regularizations;

    PendingForgottenCheckInsImpl(ManagerRegularizationRepository regularizations) {
        this.regularizations = regularizations;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> pendingByManager() {
        Map<UUID, Long> result = new HashMap<>();
        for (ManagerRegularization r : regularizations.findByStatusOrderByRequestedAtAsc(ManagerRegularization.Status.PENDING)) {
            result.merge(r.getManagerId(), 1L, Long::sum);
        }
        return result;
    }
}
