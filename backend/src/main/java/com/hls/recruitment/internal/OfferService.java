package com.hls.recruitment.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.recruitment.api.OfferAccepted;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job offers: draft, issue, supersede, decline, expire and accept. An issued offer never changes; accepting one creates
 * the Teacher (in training) from the candidate with no re-entry, refusing when the person is already a Teacher.
 */
@Service
public class OfferService {

    static final String DEFAULT_ROLE = "Trainee / English Trainer";
    private static final BigDecimal MAX_SALARY = new BigDecimal("9999999999.99");

    public record OfferRequest(
            String role,
            String monthlySalary,
            String allowances,
            String terms,
            LocalDate expectedJoining,
            LocalDate offerDate,
            LocalDate responseDeadline) {}

    public record DeclineRequest(String reason) {}

    public record AcceptRequest(Boolean confirmNewRecord) {}

    public record OfferDto(
            UUID id,
            UUID candidateId,
            String candidateName,
            String college,
            String role,
            String monthlySalary,
            String allowances,
            String terms,
            LocalDate expectedJoining,
            LocalDate offerDate,
            LocalDate responseDeadline,
            String status,
            UUID supersedesId,
            String declineReason,
            UUID issuedBy,
            String issuedByName,
            Instant issuedAt,
            Instant decidedAt,
            UUID teacherId,
            Long version) {}

    private final JobOfferRepository offers;
    private final CandidateRepository candidates;
    private final CampusDriveRepository drives;
    private final CollegeRepository colleges;
    private final AppUserRepository users;
    private final TeacherRegistry teachers;
    private final ApplicationEventPublisher events;
    private final ChangeRecorder changes;
    private final Clock clock;
    private final OfferLetterRenderer renderer;

    public OfferService(
            JobOfferRepository offers,
            CandidateRepository candidates,
            CampusDriveRepository drives,
            CollegeRepository colleges,
            AppUserRepository users,
            TeacherRegistry teachers,
            ApplicationEventPublisher events,
            ChangeRecorder changes,
            Clock clock,
            OfferLetterRenderer renderer) {
        this.offers = offers;
        this.candidates = candidates;
        this.drives = drives;
        this.colleges = colleges;
        this.users = users;
        this.teachers = teachers;
        this.events = events;
        this.changes = changes;
        this.clock = clock;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public List<OfferDto> list(String status, UUID candidateId) {
        OfferStatus wanted = null;
        if (status != null && !status.isBlank()) {
            try {
                wanted = OfferStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Unknown offer status.");
            }
        }
        return views(offers.search(wanted, candidateId));
    }

    @Transactional(readOnly = true)
    public OfferDto get(UUID id) {
        return views(List.of(find(id))).get(0);
    }

    /** The printable letter of an offer, regenerated from its current terms. */
    @Transactional(readOnly = true)
    public String letterFor(UUID id) {
        return renderer.render(get(id));
    }

    @Transactional
    public OfferDto createDraft(UUID actor, UUID candidateId, OfferRequest request) {
        Candidate candidate = candidates.findById(candidateId).orElseThrow(() -> new NotFoundException("Candidate not found."));
        if (candidate.getOutcome() != Outcome.SELECTED) {
            throw new ConflictException("Only a selected candidate can be offered.");
        }
        if (candidate.getTeacherId() != null) {
            throw new ConflictException("This candidate has already joined as a Teacher.");
        }
        requireNoOpenOffer(candidate.getPhoneKey());
        Terms terms = termsOf(request);
        JobOffer offer = new JobOffer(
                candidateId,
                candidate.getPhoneKey(),
                terms.role(),
                terms.salary(),
                terms.allowances(),
                terms.terms(),
                terms.expectedJoining(),
                terms.offerDate(),
                terms.deadline(),
                actor,
                clock.instant());
        save(offer);
        changes.recordLifecycle(actor, "JOB_OFFER", offer.getId(), "draft", candidate.getName() + " " + terms.salary().toPlainString());
        return views(List.of(offer)).get(0);
    }

    @Transactional
    public OfferDto updateDraft(UUID actor, UUID id, OfferRequest request) {
        JobOffer offer = find(id);
        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new ConflictException("An issued offer cannot be changed; send a new offer that replaces it.");
        }
        Terms terms = termsOf(request);
        changes.record(actor, "JOB_OFFER", id, "monthlySalary", offer.getMonthlySalary().toPlainString(), terms.salary().toPlainString());
        changes.record(actor, "JOB_OFFER", id, "role", offer.getRole(), terms.role());
        offer.revise(terms.role(), terms.salary(), terms.allowances(), terms.terms(), terms.expectedJoining(), terms.offerDate(), terms.deadline());
        offers.saveAndFlush(offer);
        return views(List.of(offer)).get(0);
    }

    @Transactional
    public OfferDto issue(UUID actor, UUID id) {
        JobOffer offer = lock(id);
        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new ConflictException("Only a draft can be issued.");
        }
        if (offer.getResponseDeadline().isBefore(LocalDate.now(clock))) {
            throw new ConflictException("The response deadline has already passed.");
        }
        offer.issue(actor, clock.instant());
        offers.saveAndFlush(offer);
        changes.record(actor, "JOB_OFFER", id, "status", OfferStatus.DRAFT, OfferStatus.ISSUED);
        return views(List.of(offer)).get(0);
    }

    /** Replaces an issued offer by a new, issued one; the old offer stays visible as SUPERSEDED. */
    @Transactional
    public OfferDto supersede(UUID actor, UUID id, OfferRequest request) {
        JobOffer old = lock(id);
        if (old.getStatus() != OfferStatus.ISSUED) {
            throw new ConflictException("Only an issued offer can be replaced.");
        }
        Terms terms = termsOf(request);
        old.supersede();
        offers.saveAndFlush(old);
        JobOffer replacement = new JobOffer(
                old.getCandidateId(),
                old.getPhoneKey(),
                terms.role(),
                terms.salary(),
                terms.allowances(),
                terms.terms(),
                terms.expectedJoining(),
                terms.offerDate(),
                terms.deadline(),
                actor,
                clock.instant());
        replacement.replacing(old.getId());
        replacement.issue(actor, clock.instant());
        save(replacement);
        changes.record(actor, "JOB_OFFER", old.getId(), "status", OfferStatus.ISSUED, OfferStatus.SUPERSEDED);
        changes.recordLifecycle(actor, "JOB_OFFER", replacement.getId(), "supersede", old.getId());
        return views(List.of(replacement)).get(0);
    }

    @Transactional
    public OfferDto decline(UUID actor, UUID id, DeclineRequest request) {
        JobOffer offer = lock(id);
        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new ConflictException("Only an issued offer can be declined.");
        }
        String reason = Texts.clean(request == null ? null : request.reason(), 300, "Reason");
        if (reason == null) {
            throw new InvalidInputException("A reason is required to decline an offer.");
        }
        offer.decline(reason, actor, clock.instant());
        offers.saveAndFlush(offer);
        changes.record(actor, "JOB_OFFER", id, "status", OfferStatus.ISSUED, OfferStatus.DECLINED);
        return views(List.of(offer)).get(0);
    }

    /** Accepts the offer and creates the Teacher in training; accepting twice returns the same result. */
    @Transactional
    public OfferDto accept(UUID actor, UUID id, AcceptRequest request) {
        JobOffer offer = lock(id);
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            return views(List.of(offer)).get(0);
        }
        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new ConflictException("Only an issued offer can be accepted.");
        }
        if (offer.getResponseDeadline().isBefore(LocalDate.now(clock))) {
            throw new ConflictException("This offer passed its response deadline and can no longer be accepted.");
        }
        Candidate candidate = candidates.findById(offer.getCandidateId()).orElseThrow(() -> new NotFoundException("Candidate not found."));
        List<TeacherRegistry.Match> matches = teachers.findMatches(candidate.getPhone(), candidate.getEmail());
        for (TeacherRegistry.Match match : matches) {
            if (!"EXITED".equals(match.status())) {
                throw new ConflictException("This person is already a Teacher: " + match.name() + " (" + match.status() + ").");
            }
        }
        if (!matches.isEmpty() && !Boolean.TRUE.equals(request == null ? null : request.confirmNewRecord())) {
            throw new ConflictException("An earlier Teacher record of " + matches.get(0).name()
                    + " has exited. Confirm to create a new Teacher record.");
        }
        UUID teacherId = teachers.createTrainee(
                actor, new TeacherRegistry.Candidate(candidate.getName(), candidate.getPhone(), candidate.getEmail(), null));
        offer.accept(teacherId, actor, clock.instant());
        candidate.linkTeacher(teacherId);
        candidates.save(candidate);
        offers.saveAndFlush(offer);
        changes.record(actor, "JOB_OFFER", id, "status", OfferStatus.ISSUED, OfferStatus.ACCEPTED);
        changes.recordLifecycle(actor, "TEACHER_FROM_OFFER", id, "created", teacherId);
        events.publishEvent(new OfferAccepted(id, candidate.getId(), teacherId));
        return views(List.of(offer)).get(0);
    }

    /** Marks every issued offer past its deadline as expired; returns how many. */
    @Transactional
    public int expireOverdue(UUID systemActor) {
        List<JobOffer> overdue = offers.findByStatusAndResponseDeadlineBefore(OfferStatus.ISSUED, LocalDate.now(clock));
        for (JobOffer offer : overdue) {
            offer.expire();
            offers.saveAndFlush(offer);
            changes.record(systemActor, "JOB_OFFER", offer.getId(), "status", OfferStatus.ISSUED, OfferStatus.EXPIRED);
        }
        return overdue.size();
    }

    JobOffer find(UUID id) {
        return offers.findById(id).orElseThrow(() -> new NotFoundException("Offer not found."));
    }

    private JobOffer lock(UUID id) {
        return offers.findForUpdate(id).orElseThrow(() -> new NotFoundException("Offer not found."));
    }

    private void requireNoOpenOffer(String phoneKey) {
        if (offers.findFirstByPhoneKeyAndStatusIn(phoneKey, EnumSet.of(OfferStatus.DRAFT, OfferStatus.ISSUED)).isPresent()) {
            throw new ConflictException("This person already has an open offer; supersede it instead.");
        }
    }

    private void save(JobOffer offer) {
        try {
            offers.saveAndFlush(offer);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This person already has an open or accepted offer.");
        }
    }

    private record Terms(
            String role,
            BigDecimal salary,
            String allowances,
            String terms,
            LocalDate expectedJoining,
            LocalDate offerDate,
            LocalDate deadline) {}

    private Terms termsOf(OfferRequest request) {
        String role = Texts.clean(request.role(), 60, "Role");
        BigDecimal salary;
        try {
            salary = new BigDecimal(request.monthlySalary() == null ? "" : request.monthlySalary().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("The monthly salary must be a number.");
        }
        if (salary.signum() <= 0 || salary.compareTo(MAX_SALARY) > 0) {
            throw new InvalidInputException("The monthly salary must be more than zero.");
        }
        LocalDate offerDate = request.offerDate() == null ? LocalDate.now(clock) : request.offerDate();
        if (request.responseDeadline() == null) {
            throw new InvalidInputException("A response deadline is required.");
        }
        if (request.responseDeadline().isBefore(offerDate)) {
            throw new InvalidInputException("The response deadline cannot be before the offer date.");
        }
        return new Terms(
                role == null ? DEFAULT_ROLE : role,
                salary,
                Texts.clean(request.allowances(), 300, "Allowances"),
                Texts.clean(request.terms(), 1000, "Terms"),
                request.expectedJoining(),
                offerDate,
                request.responseDeadline());
    }

    List<OfferDto> views(List<JobOffer> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Candidate> byId = candidates.findAllById(rows.stream().map(JobOffer::getCandidateId).distinct().toList()).stream()
                .collect(Collectors.toMap(Candidate::getId, c -> c));
        Map<UUID, CampusDrive> driveById = drives.findAllById(byId.values().stream().map(Candidate::getDriveId).distinct().toList()).stream()
                .collect(Collectors.toMap(CampusDrive::getId, d -> d));
        Map<UUID, College> collegeById = colleges.findAllById(driveById.values().stream().map(CampusDrive::getCollegeId).distinct().toList()).stream()
                .collect(Collectors.toMap(College::getId, c -> c));
        Set<UUID> issuers = rows.stream().map(JobOffer::getIssuedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = users.findAllById(issuers).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return rows.stream()
                .map(o -> {
                    Candidate c = byId.get(o.getCandidateId());
                    College college = collegeById.get(driveById.get(c.getDriveId()).getCollegeId());
                    return new OfferDto(
                            o.getId(),
                            c.getId(),
                            c.getName(),
                            college.getName(),
                            o.getRole(),
                            o.getMonthlySalary().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            o.getAllowances(),
                            o.getTerms(),
                            o.getExpectedJoining(),
                            o.getOfferDate(),
                            o.getResponseDeadline(),
                            o.getStatus().name(),
                            o.getSupersedesId(),
                            o.getDeclineReason(),
                            o.getIssuedBy(),
                            names.get(o.getIssuedBy()),
                            o.getIssuedAt(),
                            o.getDecidedAt(),
                            o.getTeacherId(),
                            o.getVersion());
                })
                .toList();
    }
}
