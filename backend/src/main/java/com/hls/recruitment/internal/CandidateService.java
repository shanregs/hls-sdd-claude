package com.hls.recruitment.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Candidates of a drive: one at a time or by CSV, an outcome (with history) and assessment scores. Every write needs
 * the drive to be writable by the caller (the own-drive rule of {@link DriveService}).
 */
@Service
public class CandidateService {

    static final int CHUNK = 100;

    public record NewCandidate(String name, String phone, String email, String degree, String year, String notes) {}

    public record OutcomeRequest(String outcome, String note) {}

    public record ImportError(int row, String reason) {}

    public record ImportResult(int saved, int skipped, List<ImportError> errors) {}

    public record AssessmentDto(int number, Map<String, Integer> scores, String remarks, UUID assessedBy, String assessedByName, Instant assessedAt) {}

    public record CandidateDto(
            UUID id,
            UUID driveId,
            String name,
            String phone,
            String email,
            String degree,
            String year,
            String notes,
            String outcome,
            Instant outcomeAt,
            UUID teacherId,
            AssessmentDto assessment,
            Long version) {}

    public record HistoryRow(String kind, String value, String note, UUID by, String byName, Instant at) {}

    private final CandidateRepository candidates;
    private final CandidateOutcomeHistoryRepository history;
    private final AssessmentScoreRepository scores;
    private final DriveService drives;
    private final AppUserRepository users;
    private final ChangeRecorder changes;
    private final Clock clock;
    private final TransactionTemplate chunkTx;
    private final JobOfferRepository offers;

    public CandidateService(
            CandidateRepository candidates,
            CandidateOutcomeHistoryRepository history,
            AssessmentScoreRepository scores,
            DriveService drives,
            AppUserRepository users,
            ChangeRecorder changes,
            Clock clock,
            PlatformTransactionManager txManager,
            JobOfferRepository offers) {
        this.candidates = candidates;
        this.history = history;
        this.scores = scores;
        this.drives = drives;
        this.users = users;
        this.changes = changes;
        this.clock = clock;
        this.chunkTx = new TransactionTemplate(txManager);
        this.offers = offers;
    }

    @Transactional(readOnly = true)
    public List<CandidateDto> search(UUID driveId, String outcome, String query) {
        Outcome wanted = outcome == null || outcome.isBlank() ? null : parseOutcome(outcome);
        String term = query == null ? "" : query.trim();
        return views(candidates.search(driveId, wanted, term));
    }

    @Transactional
    public CandidateDto add(UUID actor, Set<Role> roles, UUID driveId, NewCandidate request) {
        CampusDrive drive = drives.find(driveId);
        drives.requireWritable(actor, roles, drive);
        requireOpen(drive);
        Candidate candidate = build(actor, driveId, request);
        if (candidates.findByDriveIdAndPhoneKey(driveId, candidate.getPhoneKey()).isPresent()) {
            throw new ConflictException("A candidate with this phone number is already in this drive.");
        }
        candidates.saveAndFlush(candidate);
        changes.recordLifecycle(actor, "CANDIDATE", candidate.getId(), "created", candidate.getName());
        return views(List.of(candidate)).get(0);
    }

    /** Imports rows chunk by chunk (a transaction per {@value #CHUNK} rows) so a large file never holds one lock. */
    public ImportResult importCsv(UUID actor, Set<Role> roles, UUID driveId, byte[] content) {
        CampusDrive drive = drives.find(driveId);
        drives.requireWritable(actor, roles, drive);
        requireOpen(drive);
        List<CsvImporter.Row> rows = CsvImporter.parse(content);
        Set<String> seen = new HashSet<>();
        candidates.findByDriveIdIn(List.of(driveId)).forEach(c -> seen.add(c.getPhoneKey()));
        int saved = 0;
        int skipped = 0;
        List<ImportError> errors = new ArrayList<>();
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<CsvImporter.Row> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            List<Candidate> toSave = new ArrayList<>();
            for (CsvImporter.Row row : chunk) {
                try {
                    Candidate candidate = build(
                            actor,
                            driveId,
                            new NewCandidate(row.name(), row.phone(), row.email(), row.degree(), row.year(), row.notes()));
                    if (!seen.add(candidate.getPhoneKey())) {
                        skipped++;
                        errors.add(new ImportError(row.number(), "This phone number is already in the drive."));
                    } else {
                        toSave.add(candidate);
                    }
                } catch (InvalidInputException e) {
                    errors.add(new ImportError(row.number(), e.getMessage()));
                }
            }
            int ok;
            try {
                Integer done = chunkTx.execute(status -> {
                    for (Candidate c : toSave) {
                        candidates.saveAndFlush(c);
                    }
                    return toSave.size();
                });
                ok = done == null ? 0 : done;
            } catch (DataIntegrityViolationException | org.springframework.transaction.UnexpectedRollbackException e) {
                // a phone was added by someone else meanwhile: keep every other row by saving them one by one
                ok = 0;
                for (Candidate c : toSave) {
                    try {
                        chunkTx.executeWithoutResult(status -> candidates.saveAndFlush(c));
                        ok++;
                    } catch (DataIntegrityViolationException | org.springframework.transaction.UnexpectedRollbackException duplicate) {
                        // counted as skipped below
                    }
                }
            }
            saved += ok;
            skipped += toSave.size() - ok;
        }
        String summary = "saved " + saved + ", skipped " + skipped + ", errors " + errors.size();
        chunkTx.executeWithoutResult(status -> changes.recordLifecycle(actor, "CANDIDATE", driveId, "imported", summary));
        return new ImportResult(saved, skipped, errors);
    }

    @Transactional
    public CandidateDto setOutcome(UUID actor, Set<Role> roles, UUID candidateId, OutcomeRequest request) {
        Candidate candidate = find(candidateId);
        drives.requireWritable(actor, roles, drives.find(candidate.getDriveId()));
        Outcome next = parseOutcome(request.outcome());
        String note = Texts.clean(request.note(), 300, "Note");
        Outcome before = candidate.getOutcome();
        if (before == next) {
            throw new ConflictException("The candidate already has this outcome.");
        }
        if (before == Outcome.SELECTED && candidate.getTeacherId() != null) {
            throw new ConflictException("This candidate has accepted an offer and is already a Teacher.");
        }
        if (offers.existsByCandidateIdAndStatusIn(candidateId, java.util.EnumSet.of(OfferStatus.DRAFT, OfferStatus.ISSUED))) {
            throw new ConflictException("This candidate has an open offer; close it before changing the outcome.");
        }
        Instant now = clock.instant();
        candidate.decide(next, actor, now);
        candidates.saveAndFlush(candidate);
        history.save(new CandidateOutcomeHistory(candidateId, next, note, actor, now));
        changes.record(actor, "CANDIDATE", candidateId, "outcome", before, next);
        return views(List.of(candidate)).get(0);
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> historyOf(UUID candidateId) {
        find(candidateId);
        List<HistoryRow> rows = new ArrayList<>();
        Set<UUID> userIds = new HashSet<>();
        List<CandidateOutcomeHistory> outcomes = history.findByCandidateIdOrderByChangedAtAsc(candidateId);
        List<AssessmentScore> marks = scores.findByCandidateIdOrderByAssessmentNoAscCriterionAsc(candidateId);
        outcomes.forEach(o -> userIds.add(o.getChangedBy()));
        marks.forEach(m -> userIds.add(m.getAssessedBy()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        for (CandidateOutcomeHistory o : outcomes) {
            rows.add(new HistoryRow("OUTCOME", o.getOutcome().name(), o.getNote(), o.getChangedBy(), names.get(o.getChangedBy()), o.getChangedAt()));
        }
        for (AssessmentScore m : marks) {
            rows.add(new HistoryRow(
                    "ASSESSMENT " + m.getAssessmentNo(),
                    m.getCriterion() + " " + m.getScore(),
                    m.getRemarks(),
                    m.getAssessedBy(),
                    names.get(m.getAssessedBy()),
                    m.getAssessedAt()));
        }
        rows.sort(Comparator.comparing(HistoryRow::at));
        return rows;
    }

    Candidate find(UUID id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("Candidate not found."));
    }

    private Candidate build(UUID actor, UUID driveId, NewCandidate request) {
        String name = Texts.required(request.name(), 160, "Name");
        String phone = Texts.required(request.phone(), 20, "Phone");
        String key = Texts.phoneKey(phone);
        if (key == null) {
            throw new InvalidInputException("Phone must have at least 10 digits.");
        }
        return new Candidate(
                driveId,
                name,
                phone,
                key,
                Texts.email(request.email()),
                Texts.clean(request.degree(), 120, "Degree"),
                Texts.clean(request.year(), 40, "Year"),
                Texts.clean(request.notes(), 500, "Notes"),
                actor,
                clock.instant());
    }

    private static void requireOpen(CampusDrive drive) {
        if (drive.getStatus() == DriveStatus.CANCELLED) {
            throw new ConflictException("This drive was cancelled, so candidates cannot be added.");
        }
    }

    private static Outcome parseOutcome(String value) {
        try {
            return Outcome.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The outcome must be SELECTED, WAITLISTED or REJECTED.");
        }
    }

    List<CandidateDto> views(List<Candidate> rows) {
        Map<UUID, List<AssessmentScore>> marks = scores
                .findByCandidateIdIn(rows.stream().map(Candidate::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(AssessmentScore::getCandidateId));
        Set<UUID> assessors = new HashSet<>();
        marks.values().forEach(l -> l.forEach(m -> assessors.add(m.getAssessedBy())));
        Map<UUID, String> names = users.findAllById(assessors).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return rows.stream()
                .map(c -> new CandidateDto(
                        c.getId(),
                        c.getDriveId(),
                        c.getName(),
                        c.getPhone(),
                        c.getEmail(),
                        c.getDegree(),
                        c.getStudyYear(),
                        c.getNotes(),
                        c.getOutcome() == null ? null : c.getOutcome().name(),
                        c.getOutcomeAt(),
                        c.getTeacherId(),
                        latest(marks.getOrDefault(c.getId(), List.of()), names),
                        c.getVersion()))
                .toList();
    }

    private static AssessmentDto latest(List<AssessmentScore> all, Map<UUID, String> names) {
        if (all.isEmpty()) {
            return null;
        }
        int number = all.stream().mapToInt(AssessmentScore::getAssessmentNo).max().orElse(0);
        List<AssessmentScore> current = all.stream().filter(s -> s.getAssessmentNo() == number).toList();
        Map<String, Integer> byCriterion = new LinkedHashMap<>();
        current.stream()
                .sorted(Comparator.comparing(AssessmentScore::getCriterion))
                .forEach(s -> byCriterion.put(s.getCriterion(), s.getScore()));
        AssessmentScore first = current.get(0);
        return new AssessmentDto(
                number, byCriterion, first.getRemarks(), first.getAssessedBy(), names.get(first.getAssessedBy()), first.getAssessedAt());
    }
}
