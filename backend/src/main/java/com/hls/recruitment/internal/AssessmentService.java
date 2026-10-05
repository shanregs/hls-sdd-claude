package com.hls.recruitment.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assessment scores (speaking, English, communication; 1 to 5). A new assessment adds rows; the latest is current. */
@Service
public class AssessmentService {

    public record AssessmentRequest(Map<String, Integer> scores, String remarks) {}

    private final AssessmentScoreRepository scores;
    private final CandidateService candidates;
    private final DriveService drives;
    private final ChangeRecorder changes;
    private final Clock clock;

    public AssessmentService(
            AssessmentScoreRepository scores,
            CandidateService candidates,
            DriveService drives,
            ChangeRecorder changes,
            Clock clock) {
        this.scores = scores;
        this.candidates = candidates;
        this.drives = drives;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public CandidateService.CandidateDto record(UUID actor, Set<Role> roles, UUID candidateId, AssessmentRequest request) {
        Candidate candidate = candidates.find(candidateId);
        drives.requireWritable(actor, roles, drives.find(candidate.getDriveId()));
        Map<String, Integer> given = request.scores();
        if (given == null || given.isEmpty()) {
            throw new InvalidInputException("Give a score for at least one criterion.");
        }
        for (Map.Entry<String, Integer> e : given.entrySet()) {
            if (!AssessmentScore.CRITERIA.contains(e.getKey())) {
                throw new InvalidInputException("Unknown criterion " + e.getKey() + ".");
            }
            if (e.getValue() == null || e.getValue() < 1 || e.getValue() > 5) {
                throw new InvalidInputException("A score must be between 1 and 5.");
            }
        }
        String remarks = Texts.clean(request.remarks(), 300, "Remarks");
        int number = scores.latestNumber(candidateId) + 1;
        Instant now = clock.instant();
        for (Map.Entry<String, Integer> e : given.entrySet()) {
            scores.save(new AssessmentScore(candidateId, number, e.getKey(), e.getValue(), remarks, actor, now));
        }
        scores.flush();
        changes.recordLifecycle(actor, "ASSESSMENT", candidateId, "assessment " + number, new java.util.TreeMap<>(given));
        return candidates.views(java.util.List.of(candidate)).get(0);
    }
}
