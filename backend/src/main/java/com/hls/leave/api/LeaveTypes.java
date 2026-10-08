package com.hls.leave.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * What other modules may ask about leave types (spec 009 amendment A3). Payroll (spec 013a) uses it to tell an
 * unpaid Loss-of-Pay leave day from paid leave without reading leave tables: an attendance mark made by leave
 * carries the request id ({@code MarkView.leaveRequestId}); ask here whether that request is Loss-of-Pay.
 *
 * <p>Attendance holds a leave mark only while the request is approved (a cancelled, rejected or revoked
 * request has its marks removed), so a mark's request id that is Loss-of-Pay means an approved Loss-of-Pay day.
 */
public interface LeaveTypes {

    /** The stable code of the Loss-of-Pay leave type. */
    String LOSS_OF_PAY_CODE = "LOP";

    /** Whether the leave request is of the Loss-of-Pay type; false for an unknown request. */
    boolean isLossOfPay(UUID leaveRequestId);

    /** The subset of the given leave request ids that are of the Loss-of-Pay type. */
    Set<UUID> lossOfPayRequests(Collection<UUID> leaveRequestIds);
}
