package com.hls.leave.manager.internal;

import com.hls.attendance.api.PendingManagerRequests;
import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Manager leave requests still waiting for a decision, for the month lock to consult (spec 032 US6). */
@Component
public class ManagerLeavePendingRequests implements PendingManagerRequests {

    public static final String KIND = "LEAVE";

    private final ManagerLeaveRequestRepository requests;

    public ManagerLeavePendingRequests(ManagerLeaveRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pending> pendingFor(YearMonth month) {
        return requests
                .findByStatusAndFirstDateLessThanEqualAndLastDateGreaterThanEqual(
                        Status.PENDING, month.atEndOfMonth(), month.atDay(1))
                .stream()
                .map(r -> new Pending(KIND, r.getId(), r.getManagerId(), r.getFirstDate(), r.getLastDate()))
                .toList();
    }
}
