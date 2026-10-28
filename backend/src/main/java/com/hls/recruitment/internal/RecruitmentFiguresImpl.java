package com.hls.recruitment.internal;

import com.hls.recruitment.api.RecruitmentFigures;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Spec 014c A1: the dashboard figures under a public name, so a report and the dashboard cannot disagree. */
@Component
class RecruitmentFiguresImpl implements RecruitmentFigures {

    private final RecruitmentDashboardService dashboard;

    RecruitmentFiguresImpl(RecruitmentDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    @Transactional(readOnly = true)
    public Result bySeason(String seasonOrNull) {
        RecruitmentDashboardService.DashboardDto dto = dashboard.dashboard(null, seasonOrNull, null, null, false);
        return new Result(
                dto.colleges().stream()
                        .map(r -> new CollegeRow(r.collegeId(), r.name(), r.city(), convert(r.counts())))
                        .toList(),
                convert(dto.totals()),
                dto.readyToDeployTotal());
    }

    private static Counts convert(RecruitmentDashboardService.Counts c) {
        return new Counts(
                c.drivesScheduled(), c.drivesHeld(), c.interviewed(), c.assessed(), c.selected(), c.offered(), c.accepted(),
                c.inducted(), c.readyToDeploy(), c.placed(), c.active(), c.joiningRatio());
    }
}
