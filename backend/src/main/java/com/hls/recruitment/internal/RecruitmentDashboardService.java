package com.hls.recruitment.internal;

import com.hls.recruitment.api.InductionProgress;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recruitment funnel per college: drives, candidates assessed, selected, offered, joined, inducted, placed and
 * active, and the joining ratio (joined divided by selected). Bulk queries only, no per-row lookups.
 */
@Service
public class RecruitmentDashboardService {

    public record Counts(
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

    public record CollegeRow(UUID collegeId, String name, String city, Counts counts) {}

    public record DashboardDto(List<CollegeRow> colleges, Counts totals, long readyToDeployTotal) {}

    private static final Set<OfferStatus> SENT = EnumSet.of(
            OfferStatus.ISSUED, OfferStatus.ACCEPTED, OfferStatus.DECLINED, OfferStatus.EXPIRED, OfferStatus.SUPERSEDED);

    private final CampusDriveRepository drives;
    private final CollegeRepository colleges;
    private final CandidateRepository candidates;
    private final AssessmentScoreRepository scores;
    private final JobOfferRepository offers;
    private final TeacherDirectory teacherDirectory;
    private final TeacherRegistry registry;
    private final ObjectProvider<InductionProgress> induction;
    private final Clock clock;

    public RecruitmentDashboardService(
            CampusDriveRepository drives,
            CollegeRepository colleges,
            CandidateRepository candidates,
            AssessmentScoreRepository scores,
            JobOfferRepository offers,
            TeacherDirectory teacherDirectory,
            TeacherRegistry registry,
            ObjectProvider<InductionProgress> induction,
            Clock clock) {
        this.drives = drives;
        this.colleges = colleges;
        this.candidates = candidates;
        this.scores = scores;
        this.offers = offers;
        this.teacherDirectory = teacherDirectory;
        this.registry = registry;
        this.induction = induction;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(UUID userId, String season, LocalDate from, LocalDate to, boolean mine) {
        List<CampusDrive> filtered = drives.findAll().stream()
                .filter(d -> season == null || season.isBlank() || season.equalsIgnoreCase(d.getSeasonLabel()))
                .filter(d -> from == null || d.getDates().stream().anyMatch(x -> !x.isBefore(from)))
                .filter(d -> to == null || d.getDates().stream().anyMatch(x -> !x.isAfter(to)))
                .filter(d -> !mine || d.getScheduledBy().equals(userId) || d.getInterviewers().contains(userId))
                .toList();
        List<UUID> driveIds = filtered.stream().map(CampusDrive::getId).toList();
        List<Candidate> people = driveIds.isEmpty() ? List.of() : candidates.findByDriveIdIn(driveIds);
        List<UUID> candidateIds = people.stream().map(Candidate::getId).toList();
        Set<UUID> assessed = candidateIds.isEmpty()
                ? Set.of()
                : scores.findByCandidateIdIn(candidateIds).stream().map(AssessmentScore::getCandidateId).collect(Collectors.toSet());
        Set<UUID> offered = candidateIds.isEmpty()
                ? Set.of()
                : offers.findByCandidateIdIn(candidateIds).stream()
                        .filter(o -> SENT.contains(o.getStatus()))
                        .map(JobOffer::getCandidateId)
                        .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(clock);
        Set<UUID> completed = induction.stream().findFirst().map(InductionProgress::completedTeacherIds).orElse(Set.of());
        Set<UUID> placedNow = teacherDirectory.teachersPlacedDuring(today, today);
        Set<UUID> activeTeachers = registry.activeTeacherIds();

        Map<UUID, List<CampusDrive>> drivesByCollege = filtered.stream().collect(Collectors.groupingBy(CampusDrive::getCollegeId));
        Map<UUID, List<Candidate>> peopleByDrive = people.stream().collect(Collectors.groupingBy(Candidate::getDriveId));
        Map<UUID, College> collegeById = colleges.findAllById(drivesByCollege.keySet()).stream()
                .collect(Collectors.toMap(College::getId, c -> c));

        List<CollegeRow> rows = drivesByCollege.entrySet().stream()
                .map(e -> {
                    List<Candidate> own = e.getValue().stream()
                            .flatMap(d -> peopleByDrive.getOrDefault(d.getId(), List.of()).stream())
                            .toList();
                    College college = collegeById.get(e.getKey());
                    return new CollegeRow(
                            college.getId(), college.getName(), college.getCity(),
                            counts(e.getValue(), own, assessed, offered, completed, placedNow, activeTeachers));
                })
                .sorted(Comparator.comparing(CollegeRow::name))
                .toList();
        Counts totals = counts(filtered, people, assessed, offered, completed, placedNow, activeTeachers);
        Set<UUID> ready = new HashSet<>(completed);
        ready.retainAll(activeTeachers);
        ready.removeAll(placedNow);
        return new DashboardDto(rows, totals, ready.size());
    }

    private static Counts counts(
            List<CampusDrive> drives,
            List<Candidate> people,
            Set<UUID> assessed,
            Set<UUID> offered,
            Set<UUID> completed,
            Set<UUID> placedNow,
            Set<UUID> activeTeachers) {
        long selected = people.stream().filter(c -> c.getOutcome() == Outcome.SELECTED).count();
        List<UUID> joinedTeachers = people.stream().map(Candidate::getTeacherId).filter(java.util.Objects::nonNull).toList();
        long accepted = joinedTeachers.size();
        long inducted = joinedTeachers.stream().filter(completed::contains).count();
        long placed = joinedTeachers.stream().filter(placedNow::contains).count();
        long active = joinedTeachers.stream().filter(activeTeachers::contains).count();
        long ready = joinedTeachers.stream().filter(t -> completed.contains(t) && activeTeachers.contains(t) && !placedNow.contains(t)).count();
        String ratio = selected == 0
                ? null
                : BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf(selected), 2, RoundingMode.HALF_UP).toPlainString();
        return new Counts(
                drives.stream().filter(d -> d.getStatus() != DriveStatus.CANCELLED).count(),
                drives.stream().filter(d -> d.getStatus() == DriveStatus.HELD).count(),
                people.size(),
                people.stream().filter(c -> assessed.contains(c.getId())).count(),
                selected,
                people.stream().filter(c -> offered.contains(c.getId())).count(),
                accepted,
                inducted,
                ready,
                placed,
                active,
                ratio);
    }
}
