package com.hls.organization.api;

import com.hls.organization.api.dto.AccountabilityAnswer;
import com.hls.organization.api.dto.AssignmentHistoryEntry;
import com.hls.organization.api.dto.ItemType;
import com.hls.organization.api.dto.PortfolioItem;
import com.hls.organization.api.dto.UnassignedItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public read surface for Manager/School/Teacher accountability (FR-005/007/008/009).
 * {@code currentManagerForSchool}/{@code currentManagerForTeacher} are the two
 * methods other modules depend on for scope-checking — their signatures are
 * locked to match Identity's (spec 002) already-tested stand-in exactly
 * (research.md §7).
 */
public interface AccountabilityQueries {

    AccountabilityAnswer currentManagerForSchool(UUID schoolId);

    AccountabilityAnswer currentManagerForTeacher(UUID teacherId);

    /** FR-005: "who was accountable as of date D," for any past date, not only now. */
    AccountabilityAnswer managerForSchoolAsOf(UUID schoolId, Instant asOf);

    AccountabilityAnswer managerForTeacherAsOf(UUID teacherId, Instant asOf);

    /** FR-005/FR-006: full history, oldest first, no gaps or overlaps. */
    List<AssignmentHistoryEntry> schoolAssignmentHistory(UUID schoolId);

    List<AssignmentHistoryEntry> teacherAssignmentHistory(UUID teacherId);

    /** FR-008: every School and Teacher currently accountable to this Manager. */
    List<PortfolioItem> portfolioForManager(UUID managerId);

    /** FR-009: every School/Teacher with no current accountable Manager. {@code filter} may be null for both types. */
    List<UnassignedItem> unassigned(ItemType filter);
}
