package com.hls.leave.internal;

import com.hls.leave.api.LeaveTypes;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 009 amendment A3: answers {@link LeaveTypes} from the leave type catalog and the requests. */
@Service
class LeaveTypesImpl implements LeaveTypes {

    private final LeaveRequestRepository requests;
    private final LeaveTypeCatalog catalog;

    LeaveTypesImpl(LeaveRequestRepository requests, LeaveTypeCatalog catalog) {
        this.requests = requests;
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLossOfPay(UUID leaveRequestId) {
        return leaveRequestId != null && !lossOfPayRequests(Set.of(leaveRequestId)).isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> lossOfPayRequests(Collection<UUID> leaveRequestIds) {
        if (leaveRequestIds == null || leaveRequestIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> lopTypes = catalog.all().stream()
                .filter(t -> LOSS_OF_PAY_CODE.equals(t.getCode()))
                .map(LeaveType::getId)
                .collect(Collectors.toSet());
        return requests.findAllById(leaveRequestIds).stream()
                .filter(r -> lopTypes.contains(r.getLeaveTypeId()))
                .map(LeaveRequest::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
