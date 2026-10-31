package com.hls.recruitment.api;

import java.util.List;
import java.util.UUID;

/**
 * The recruitment funnel per college and in total, for reports (spec 014c A1). Read-only and the very same figures as the
 * Recruitment dashboard (spec 016): it delegates to the service behind that screen. Callers apply their own permission;
 * drives and colleges carry no Zone, so there is no Zone narrowing, exactly as on the dashboard.
 */
public interface RecruitmentFigures {

    /**
     * The funnel counts: {@code interviewed} is every candidate at the drives, {@code assessed} those with a score,
     * {@code accepted} the candidates who joined as Teachers, and the joining ratio is joined over selected (null when none
     * was selected).
     */
    record Counts(
            long drivesScheduled,
            long drivesHeld,
            long interviewed,
            long assessed,
            long selected,
            long offered,
            long accepted,
            long inducted,
            long readyToDeploy,
            long placed,
            long active,
            String joiningRatio) {}

    record CollegeRow(UUID collegeId, String name, String city, Counts counts) {}

    record Result(List<CollegeRow> colleges, Counts totals, long readyToDeployTotal) {}

    /** The figures of the drives of one season label (case-insensitive); null or blank means every season. */
    Result bySeason(String seasonOrNull);
}
