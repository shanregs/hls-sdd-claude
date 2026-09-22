package com.hls.school.internal;

import com.hls.school.api.ZoneAssignmentConflictException;
import com.hls.school.api.ZoneCommands;
import com.hls.school.api.ZoneQueries;
import com.hls.school.api.dto.CurrentSchoolZoneAssignment;
import com.hls.school.api.dto.SchoolZoneAnswer;
import com.hls.school.api.dto.ZoneView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements both {@link ZoneQueries} and {@link ZoneCommands} — one class,
 * mirroring {@code organization.internal.AccountabilityService}'s own
 * reasoning: every operation shares the same "current row has no
 * effective_to" model, and splitting query/command implementations would
 * just duplicate that.
 */
@Service
public class ZoneService implements ZoneQueries, ZoneCommands {

    private final ZoneRepository zoneRepository;
    private final SchoolZoneAssignmentRepository assignmentRepository;
    private final Clock clock;

    public ZoneService(ZoneRepository zoneRepository, SchoolZoneAssignmentRepository assignmentRepository, Clock clock) {
        this.zoneRepository = zoneRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    // ---- Queries -----------------------------------------------------------------------

    @Override
    public Optional<ZoneView> findById(UUID zoneId) {
        return zoneRepository.findById(zoneId).map(z -> new ZoneView(z.getId(), z.getName()));
    }

    @Override
    public List<UUID> currentSchoolsForZone(UUID zoneId) {
        return assignmentRepository.findByZoneIdAndEffectiveToIsNull(zoneId).stream()
                .map(SchoolZoneAssignment::getSchoolId)
                .toList();
    }

    @Override
    public SchoolZoneAnswer currentZoneForSchool(UUID schoolId) {
        return assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)
                .map(a -> SchoolZoneAnswer.currentZone(a.getZoneId()))
                .orElseGet(SchoolZoneAnswer::unassigned);
    }

    // ---- Commands ----------------------------------------------------------------------

    @Override
    @Transactional
    public UUID createZone(String name, UUID actingUserId) {
        Zone zone = new Zone(UUID.randomUUID(), name, clock.instant(), actingUserId);
        zoneRepository.save(zone);
        return zone.getId();
    }

    @Override
    @Transactional
    public CurrentSchoolZoneAssignment assignSchoolToZone(UUID schoolId, UUID zoneId, UUID endsAssignmentId, UUID actingUserId) {
        Optional<SchoolZoneAssignment> current = assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId);
        Instant now = clock.instant();

        if (endsAssignmentId == null) {
            if (current.isPresent()) {
                if (current.get().getZoneId().equals(zoneId)) {
                    // FR-010-equivalent: no-op, no new row (mirrors AccountabilityService.assignSchoolManager).
                    SchoolZoneAssignment existing = current.get();
                    return new CurrentSchoolZoneAssignment(existing.getId(), existing.getZoneId(), existing.getEffectiveFrom());
                }
                throw new ZoneAssignmentConflictException(
                        "School " + schoolId + " already has a current Zone; supply endsAssignmentId to reassign");
            }
            SchoolZoneAssignment created = new SchoolZoneAssignment(UUID.randomUUID(), schoolId, zoneId, now, actingUserId, now);
            assignmentRepository.save(created);
            return new CurrentSchoolZoneAssignment(created.getId(), zoneId, now);
        }

        int updated = assignmentRepository.endIfStillCurrent(endsAssignmentId, now);
        if (updated == 0) {
            throw new ZoneAssignmentConflictException(
                    "School-Zone assignment " + endsAssignmentId + " was already changed by someone else");
        }
        SchoolZoneAssignment created = new SchoolZoneAssignment(UUID.randomUUID(), schoolId, zoneId, now, actingUserId, now);
        assignmentRepository.save(created);
        return new CurrentSchoolZoneAssignment(created.getId(), zoneId, now);
    }
}
