package com.hls.audit.logs;

import com.hls.audit.changehistory.ChangeHistoryEntry;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
import com.hls.audit.loginhistory.LoginHistoryEntry;
import com.hls.audit.loginhistory.LoginHistoryEntryRepository;
import com.hls.audit.useractivity.UserActivityEntry;
import com.hls.audit.useractivity.UserActivityEntryRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Composes the unified Audit Logs feed (FR-008, US4) over the three typed audit tables by
 * fetching each filtered/sorted list and merging in memory (research.md §3) — equivalent to a
 * `UNION ALL` at this project's scale (<100 users, Constitution deployment constraint), without
 * the fragility of hand-written native SQL across three differently-shaped tables.
 */
@Service
public class AuditLogQueryService {

    public static final Set<String> ALL_TYPES = Set.of("LOGIN", "CHANGE", "ACTIVITY");

    private final LoginHistoryEntryRepository loginHistoryEntryRepository;
    private final ChangeHistoryEntryRepository changeHistoryEntryRepository;
    private final UserActivityEntryRepository userActivityEntryRepository;

    public AuditLogQueryService(
            LoginHistoryEntryRepository loginHistoryEntryRepository,
            ChangeHistoryEntryRepository changeHistoryEntryRepository,
            UserActivityEntryRepository userActivityEntryRepository) {
        this.loginHistoryEntryRepository = loginHistoryEntryRepository;
        this.changeHistoryEntryRepository = changeHistoryEntryRepository;
        this.userActivityEntryRepository = userActivityEntryRepository;
    }

    /** Unfiltered query (no entity type hidden). */
    public Page<AuditLogEntryView> query(Set<String> types, UUID userId, Instant from, Instant to, Pageable pageable) {
        return query(types, userId, from, to, Set.of(), pageable);
    }

    public Page<AuditLogEntryView> query(
            Set<String> types, UUID userId, Instant from, Instant to, Set<String> hiddenEntityTypes, Pageable pageable) {
        List<AuditLogEntryView> combined = new ArrayList<>();
        Sort byOccurredAtDesc = Sort.by(Sort.Direction.DESC, "occurredAt");

        if (types.contains("LOGIN")) {
            loginHistoryEntryRepository.findAll(loginSpec(userId, from, to), byOccurredAtDesc).stream()
                    .map(AuditLogQueryService::toView)
                    .forEach(combined::add);
        }
        if (types.contains("CHANGE")) {
            changeHistoryEntryRepository.findAll(changeSpec(userId, from, to, hiddenEntityTypes), byOccurredAtDesc).stream()
                    .map(AuditLogQueryService::toView)
                    .forEach(combined::add);
        }
        if (types.contains("ACTIVITY")) {
            userActivityEntryRepository.findAll(activitySpec(userId, from, to), byOccurredAtDesc).stream()
                    .map(AuditLogQueryService::toView)
                    .forEach(combined::add);
        }

        combined.sort(Comparator.comparing(AuditLogEntryView::occurredAt).reversed());

        int start = Math.min((int) pageable.getOffset(), combined.size());
        int end = Math.min(start + pageable.getPageSize(), combined.size());
        return new PageImpl<>(combined.subList(start, end), pageable, combined.size());
    }

    private static AuditLogEntryView toView(LoginHistoryEntry entry) {
        String summary = "Sign-in (" + entry.getMethod() + ") " + entry.getOutcome().toLowerCase(java.util.Locale.ROOT);
        return new AuditLogEntryView(
                entry.getOccurredAt(),
                "LOGIN",
                entry.getUserId(),
                summary,
                entry.getOrigin().getSource(),
                entry.getOrigin().getAppVersion(),
                entry.getOrigin().location(),
                entry.isDeviceRooted());
    }

    private static AuditLogEntryView toView(ChangeHistoryEntry entry) {
        String summary = entry.getEntityId() + "." + entry.getField() + " set to " + entry.getAfterValue();
        return new AuditLogEntryView(entry.getOccurredAt(), "CHANGE", entry.getActorUserId(), summary);
    }

    private static AuditLogEntryView toView(UserActivityEntry entry) {
        String summary = entry.getAction().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return new AuditLogEntryView(
                entry.getOccurredAt(),
                "ACTIVITY",
                entry.getActorUserId(),
                summary,
                entry.getOrigin().getSource(),
                entry.getOrigin().getAppVersion(),
                entry.getOrigin().location(),
                null);
    }

    private static Specification<LoginHistoryEntry> loginSpec(UUID userId, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            addDateRange(predicates, root, cb, from, to);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<ChangeHistoryEntry> changeSpec(
            UUID userId, Instant from, Instant to, Set<String> hiddenEntityTypes) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!hiddenEntityTypes.isEmpty()) {
                predicates.add(cb.not(root.get("entityType").in(hiddenEntityTypes)));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), userId));
            }
            addDateRange(predicates, root, cb, from, to);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<UserActivityEntry> activitySpec(UUID userId, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("affectedUserId"), userId));
            }
            addDateRange(predicates, root, cb, from, to);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addDateRange(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Root<?> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Instant from,
            Instant to) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
    }
}
