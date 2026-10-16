package com.hls.payroll.run.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed, reasoned amounts against a person that wait for a run to take them (spec 013b US4, research 11). Only
 * {@code CORRECTION} can be recorded now; the other kinds are reserved for the specs named in the refusal. A waiting
 * adjustment is copied into a draft line at compute time and attached to that run; approval marks it applied. Applied
 * adjustments never change; a waiting one can be withdrawn with a reason unless a run awaiting approval holds it.
 */
@Service
public class AdjustmentService {

    static final String ENTITY = "PAYROLL_ADJUSTMENT";
    private static final int MAX_PAGE_SIZE = 200;
    private static final Map<PayAdjustment.AdjustmentKind, String> RESERVED_BY = Map.of(
            PayAdjustment.AdjustmentKind.INCENTIVE, "spec 030",
            PayAdjustment.AdjustmentKind.REIMBURSEMENT, "spec 015",
            PayAdjustment.AdjustmentKind.SUBSTITUTE_CREDIT, "spec 017b");

    public record Person(UUID id, String name) {}

    public record AdjustmentView(
            UUID id,
            Kind personKind,
            UUID personId,
            String personName,
            PayAdjustment.AdjustmentKind kind,
            String amount,
            String reason,
            PayAdjustment.Status status,
            UUID attachedRunId,
            UUID appliedRunId,
            UUID appliedLineId,
            Person createdBy,
            Instant createdAt,
            Person withdrawnBy,
            Instant withdrawnAt,
            String withdrawReason) {}

    private final PayAdjustmentRepository adjustments;
    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final LineAdjustmentRepository lineAdjustments;
    private final RunEventRepository events;
    private final PersonDirectory people;
    private final AppUserRepository users;
    private final ChangeRecorder changes;
    private final Clock clock;

    AdjustmentService(
            PayAdjustmentRepository adjustments,
            PayRunRepository runs,
            PayRunLineRepository lines,
            LineAdjustmentRepository lineAdjustments,
            RunEventRepository events,
            PersonDirectory people,
            AppUserRepository users,
            ChangeRecorder changes,
            Clock clock) {
        this.adjustments = adjustments;
        this.runs = runs;
        this.lines = lines;
        this.lineAdjustments = lineAdjustments;
        this.events = events;
        this.people = people;
        this.users = users;
        this.changes = changes;
        this.clock = clock;
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public AdjustmentView record(
            UUID actor, String personKindText, UUID personId, String kindText, BigDecimal amount, String reason) {
        Kind personKind = parse(Kind.class, personKindText, "person kind");
        PayAdjustment.AdjustmentKind kind = parse(PayAdjustment.AdjustmentKind.class, kindText, "kind");
        if (RESERVED_BY.containsKey(kind)) {
            throw new InvalidInputException("The kind " + kindText.trim().toUpperCase(Locale.ROOT)
                    + " is created by " + RESERVED_BY.get(kind) + ", not by hand.");
        }
        if (personId == null) {
            throw new InvalidInputException("The person is required.");
        }
        BigDecimal checked = RunInput.adjustmentAmount(amount);
        String why = RunInput.requiredReason(reason, "an adjustment");
        String name = nameOf(personKind, personId);
        PayAdjustment saved = adjustments.save(
                new PayAdjustment(personKind, personId, name, kind, checked, why, actor, clock.instant()));
        changes.recordLifecycle(
                actor, ENTITY, saved.getId(), "created", kind + " " + RunInput.rupees(checked) + " for " + name + ": " + why);
        return view(saved, names(Set.of(actor)));
    }

    /**
     * Takes a waiting adjustment back with a reason. While a draft holds it, its copy leaves the line and the net is
     * worked out again by arithmetic on the stored line; a run awaiting approval or an applied adjustment refuses.
     */
    @Transactional
    public AdjustmentView withdraw(UUID actor, UUID adjustmentId, String reason) {
        String why = RunInput.requiredReason(reason, "withdrawing an adjustment");
        PayAdjustment adjustment =
                adjustments.findById(adjustmentId).orElseThrow(() -> new NotFoundException("Adjustment not found."));
        switch (adjustment.getStatus()) {
            case APPLIED -> throw new ConflictException("This adjustment was applied in an approved run and cannot be withdrawn.");
            case WITHDRAWN -> throw new ConflictException("This adjustment was already withdrawn.");
            case WAITING -> { /* may be withdrawn, below */ }
        }
        Instant now = clock.instant();
        UUID runId = adjustment.getAttachedRunId();
        if (runId != null) {
            PayRun run = runs.lockById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
            if (run.getStatus() == PayRun.Status.AWAITING_APPROVAL) {
                throw new ConflictException("This adjustment is in the pay run for " + RunInput.monthName(run.getMonth())
                        + ", which is awaiting approval. Ask for the run to be returned first.");
            }
            if (run.getStatus() == PayRun.Status.DRAFT) {
                takeOutOfDraft(actor, run, adjustment, why, now);
            }
        }
        adjustment.withdraw(actor, why, now);
        changes.record(actor, ENTITY, adjustment.getId(), "status", PayAdjustment.Status.WAITING, PayAdjustment.Status.WITHDRAWN);
        changes.recordLifecycle(actor, ENTITY, adjustment.getId(), "withdrawn", why);
        return view(adjustment, names(new java.util.HashSet<>(java.util.Arrays.asList(actor, adjustment.getCreatedBy()))));
    }

    /** The line's copy goes, its adjustments and net are worked out again from what is stored, and the run's totals follow. */
    private void takeOutOfDraft(UUID actor, PayRun run, PayAdjustment adjustment, String why, Instant now) {
        for (LineAdjustment copy : lineAdjustments.findByAdjustmentId(adjustment.getId())) {
            PayRunLine line = lines.findById(copy.getLineId()).orElse(null);
            if (line == null || !line.getRunId().equals(run.getId())) {
                continue;
            }
            String working = withoutAdjustment(line.getWorking(), adjustment.getId());
            line.removeAdjustment(
                    copy.getAmount(),
                    working,
                    LineCalculator.fingerprintOf(line.getPersonName(), line.getPersonLabel(), line.getWarning(), working));
            lineAdjustments.delete(copy);
        }
        lines.flush();
        RunTotals.refresh(run, lines.findByRunId(run.getId()));
        events.save(new RunEvent(
                run.getId(),
                now,
                actor,
                "ADJUSTMENT_WITHDRAWN",
                why,
                Json.write(Map.of("adjustmentId", adjustment.getId().toString(), "person", adjustment.getPersonName()))));
    }

    @SuppressWarnings("unchecked")
    private static String withoutAdjustment(String workingJson, UUID adjustmentId) {
        Map<String, Object> working = new LinkedHashMap<>(JsonParserFactory.getJsonParser().parseMap(workingJson));
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> entry : (List<Map<String, Object>>) working.get("adjustments")) {
            if (!adjustmentId.toString().equals(entry.get("id"))) {
                kept.add(entry);
            }
        }
        working.put("adjustments", kept);
        return Json.write(working);
    }

    // -------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Page<AdjustmentView> list(String personKindText, UUID personId, String statusText, int page, int size) {
        PayAdjustment.Status status =
                statusText == null || statusText.isBlank() ? null : parse(PayAdjustment.Status.class, statusText, "status");
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        Page<PayAdjustment> found = personKindText != null && !personKindText.isBlank() && personId != null
                ? adjustments.searchPerson(parse(Kind.class, personKindText, "person kind"), personId, status, paging)
                : adjustments.search(status, paging);
        Set<UUID> actors = found.getContent().stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getCreatedBy(), a.getWithdrawnBy()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = names(actors);
        return found.map(a -> view(a, names));
    }

    // ------------------------------------------------------------------ helpers

    private String nameOf(Kind kind, UUID id) {
        return switch (kind) {
            case TEACHER -> people.allTeachers().stream()
                    .filter(t -> t.id().equals(id))
                    .map(PersonDirectory.TeacherSummary::name)
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException("No such Teacher."));
            case MANAGER -> people.allManagers().stream()
                    .filter(m -> m.id().equals(id))
                    .map(PersonDirectory.ManagerSummary::name)
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException("No such Manager."));
            case STAFF -> {
                PersonDirectory.StaffUser staff = people.staff(Set.of(id)).get(id);
                if (staff == null) {
                    throw new InvalidInputException("No such staff member.");
                }
                yield staff.name();
            }
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("The " + label + " is required.");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown " + label + " \"" + raw.trim() + "\".");
        }
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        Map<UUID, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AppUser user : users.findAllById(ids)) {
                map.put(user.getId(), user.getDisplayName());
            }
        }
        return map;
    }

    private static Person person(UUID id, Map<UUID, String> names) {
        return id == null ? null : new Person(id, names.getOrDefault(id, "Unknown user"));
    }

    private static AdjustmentView view(PayAdjustment a, Map<UUID, String> names) {
        return new AdjustmentView(
                a.getId(),
                a.getPersonKind(),
                a.getPersonId(),
                a.getPersonName(),
                a.getKind(),
                a.getAmount().setScale(2).toPlainString(),
                a.getReason(),
                a.getStatus(),
                a.getAttachedRunId(),
                a.getAppliedRunId(),
                a.getAppliedLineId(),
                person(a.getCreatedBy(), names),
                a.getCreatedAt(),
                person(a.getWithdrawnBy(), names),
                a.getWithdrawnAt(),
                a.getWithdrawReason());
    }
}
