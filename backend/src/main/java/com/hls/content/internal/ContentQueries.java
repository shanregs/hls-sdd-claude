package com.hls.content.internal;

import com.hls.content.internal.ContentService.Caller;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignment;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads for the content screens (spec 029): the lists, the counts, the detail and the possible authors. */
@Service
public class ContentQueries {

    public record Row(
            UUID id,
            String code,
            String title,
            String category,
            String subject,
            UUID authorUserId,
            String authorName,
            String status,
            String statusLabel,
            int currentVersion,
            LocalDate dueOn,
            boolean overdue,
            Instant updatedAt) {}

    public record Summary(long draft, long assigned, long submitted, long approved, long returned, long overdue) {}

    public record Author(UUID userId, String displayName, List<String> roles) {}

    public record VersionView(
            int number,
            String fileName,
            String contentType,
            long sizeBytes,
            UUID authorUserId,
            String authorName,
            UUID submittedByUserId,
            String submittedByName,
            String note,
            Instant submittedAt,
            boolean removed,
            String removalReason,
            boolean lastApproved) {}

    public record ReviewView(int versionNumber, UUID reviewerUserId, String reviewerName, String decision, String comment, Instant decidedAt) {}

    public record HistoryLine(String event, String actorName, Instant at, String before, String after, String note) {}

    public record Actions(boolean submit, boolean edit, boolean approve, boolean returnForRevision, boolean removeFile) {}

    public record Detail(
            UUID id,
            String code,
            String title,
            String category,
            String subject,
            String description,
            UUID authorUserId,
            String authorName,
            UUID createdByUserId,
            String createdByName,
            String status,
            String statusLabel,
            int currentVersion,
            Integer lastApprovedVersion,
            LocalDate dueOn,
            boolean overdue,
            String reviewerName,
            LocalDate approvedOn,
            UUID taskId,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<VersionView> versions,
            List<ReviewView> reviews,
            List<HistoryLine> history,
            Actions actions) {}

    private final ContentRepository contents;
    private final ContentVersionRepository versions;
    private final ContentReviewRepository reviews;
    private final ContentHistoryRepository history;
    private final ContentService service;
    private final ContentRules rules;
    private final AppUserRepository users;
    private final RoleAssignmentRepository roleAssignments;
    private final PermissionMatrixService matrix;
    private final BusinessDate businessDate;

    public ContentQueries(
            ContentRepository contents,
            ContentVersionRepository versions,
            ContentReviewRepository reviews,
            ContentHistoryRepository history,
            ContentService service,
            ContentRules rules,
            AppUserRepository users,
            RoleAssignmentRepository roleAssignments,
            PermissionMatrixService matrix,
            BusinessDate businessDate) {
        this.contents = contents;
        this.versions = versions;
        this.reviews = reviews;
        this.history = history;
        this.service = service;
        this.rules = rules;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.matrix = matrix;
        this.businessDate = businessDate;
    }

    // ------------------------------------------------------------------ scope

    /** The rows the caller may see: everything for Admin and Director, otherwise authored, created or submitted by them. */
    Specification<Content> scope(Caller caller) {
        if (caller.orgWide()) {
            return (root, q, cb) -> cb.conjunction();
        }
        Set<UUID> submitted = versions.findBySubmittedByUserId(caller.userId()).stream()
                .map(ContentVersion::getContentId)
                .collect(Collectors.toSet());
        return (root, q, cb) -> {
            List<Predicate> any = new ArrayList<>();
            any.add(cb.equal(root.get("authorUserId"), caller.userId()));
            any.add(cb.equal(root.get("createdByUserId"), caller.userId()));
            if (!submitted.isEmpty()) {
                any.add(root.get("id").in(submitted));
            }
            return cb.or(any.toArray(new Predicate[0]));
        };
    }

    private boolean overdue(Content c) {
        return c.getDueOn() != null && c.getDueOn().isBefore(businessDate.today()) && ContentStatus.UNDELIVERED.contains(c.getStatus());
    }

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public PageResponse<Row> list(
            Caller caller,
            UUID authorUserId,
            String category,
            String subject,
            String status,
            String view,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        Specification<Content> spec = scope(caller);
        if (authorUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("authorUserId"), authorUserId));
        }
        if (category != null && !category.isBlank()) {
            String like = "%" + category.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("category")), like));
        }
        if (subject != null && !subject.isBlank()) {
            String like = "%" + subject.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("subject")), like));
        }
        if (status != null && !status.isBlank()) {
            ContentStatus parsed = parseStatus(status);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), parsed));
        }
        if ("mine".equalsIgnoreCase(view)) {
            UUID me = caller.userId();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("authorUserId"), me));
        }
        if (from != null) {
            Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("updatedAt"), end));
        }
        var found = contents.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(Sort.Direction.DESC, "updatedAt")));
        Map<UUID, String> names = names(found.getContent().stream().map(Content::getAuthorUserId).collect(Collectors.toSet()));
        List<Row> rows = found.getContent().stream().map(c -> row(c, names)).toList();
        return new PageResponse<>(rows, found.getNumber(), found.getSize(), found.getTotalElements());
    }

    private static ContentStatus parseStatus(String status) {
        try {
            return ContentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.hls.school.api.InvalidInputException("Unknown status " + status + ".");
        }
    }

    private Row row(Content c, Map<UUID, String> names) {
        return new Row(
                c.getId(), c.getCode(), c.getTitle(), c.getCategory(), c.getSubject(), c.getAuthorUserId(),
                names.getOrDefault(c.getAuthorUserId(), "Unknown"), c.getStatus().name(), c.getStatus().label(),
                c.getCurrentVersion(), c.getDueOn(), overdue(c), c.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Summary summary(Caller caller) {
        List<Content> all = contents.findAll(scope(caller));
        long overdue = all.stream().filter(this::overdue).count();
        return new Summary(
                count(all, ContentStatus.DRAFT), count(all, ContentStatus.ASSIGNED), count(all, ContentStatus.SUBMITTED),
                count(all, ContentStatus.APPROVED), count(all, ContentStatus.RETURNED), overdue);
    }

    private static long count(Collection<Content> all, ContentStatus status) {
        return all.stream().filter(c -> c.getStatus() == status).count();
    }

    /** Counts for the management dashboard tiles. */
    @Transactional(readOnly = true)
    public Summary organizationSummary() {
        List<Content> all = contents.findAll();
        return new Summary(
                count(all, ContentStatus.DRAFT), count(all, ContentStatus.ASSIGNED), count(all, ContentStatus.SUBMITTED),
                count(all, ContentStatus.APPROVED), count(all, ContentStatus.RETURNED), all.stream().filter(this::overdue).count());
    }

    /** Submitted content waiting for a reviewer, for the approvals inbox. */
    @Transactional(readOnly = true)
    public List<Content> awaitingReview() {
        return contents.findAll((root, q, cb) -> cb.equal(root.get("status"), ContentStatus.SUBMITTED));
    }

    // ------------------------------------------------------------------ authors

    @Transactional(readOnly = true)
    public List<Author> authors(Caller caller) {
        List<AppUser> candidates = caller.orgWide()
                ? users.findAll().stream().filter(AppUser::isActive).toList()
                : users.findById(caller.userId()).stream().toList();
        List<Author> found = new ArrayList<>();
        for (AppUser user : candidates) {
            if (!rules.canAuthor(user.getId())) {
                continue;
            }
            List<String> roles = roleAssignments.findByUserId(user.getId()).stream()
                    .map(RoleAssignment::getRole)
                    .map(Role::name)
                    .sorted()
                    .toList();
            found.add(new Author(user.getId(), user.getDisplayName(), roles));
        }
        found.sort(Comparator.comparing(Author::displayName, String.CASE_INSENSITIVE_ORDER));
        return found;
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public Detail detail(Caller caller, UUID id) {
        Content c = service.visible(caller, id);
        List<ContentVersion> versionRows = versions.findByContentIdOrderByNumber(id);
        List<ContentReview> reviewRows = reviews.findByContentIdOrderByDecidedAt(id);
        List<ContentHistory> historyRows = history.findByContentIdOrderByAt(id);
        Set<UUID> ids = new java.util.HashSet<>();
        ids.add(c.getAuthorUserId());
        ids.add(c.getCreatedByUserId());
        if (c.getReviewerUserId() != null) {
            ids.add(c.getReviewerUserId());
        }
        versionRows.forEach(v -> {
            ids.add(v.getAuthorUserId());
            ids.add(v.getSubmittedByUserId());
        });
        reviewRows.forEach(r -> ids.add(r.getReviewerUserId()));
        historyRows.forEach(h -> {
            if (h.getActorUserId() != null) {
                ids.add(h.getActorUserId());
            }
        });
        Map<UUID, String> names = names(ids);
        ContentVersion current = versionRows.stream().filter(v -> v.getNumber() == c.getCurrentVersion()).findFirst().orElse(null);
        Set<Role> roles = caller.roles();
        boolean edit = service.canWrite(caller, c) && anyGranted(roles, PermissionAction.EDIT);
        boolean submit = edit && ContentStatus.OPEN_FOR_SUBMISSION.contains(c.getStatus());
        boolean review = anyGranted(roles, PermissionAction.APPROVE) && service.canReview(caller, c, current);
        boolean remove = caller.orgWide() && anyGranted(roles, PermissionAction.EDIT);
        return new Detail(
                c.getId(), c.getCode(), c.getTitle(), c.getCategory(), c.getSubject(), c.getDescription(),
                c.getAuthorUserId(), names.getOrDefault(c.getAuthorUserId(), "Unknown"),
                c.getCreatedByUserId(), names.getOrDefault(c.getCreatedByUserId(), "Unknown"),
                c.getStatus().name(), c.getStatus().label(), c.getCurrentVersion(), c.getLastApprovedVersion(), c.getDueOn(),
                overdue(c), c.getReviewerUserId() == null ? null : names.get(c.getReviewerUserId()), c.getApprovedOn(), c.getTaskId(),
                c.getVersion(), c.getCreatedAt(), c.getUpdatedAt(),
                versionRows.stream()
                        .map(v -> new VersionView(
                                v.getNumber(), v.getFileName(), v.getContentType(), v.getSizeBytes(), v.getAuthorUserId(),
                                names.getOrDefault(v.getAuthorUserId(), "Unknown"), v.getSubmittedByUserId(),
                                names.getOrDefault(v.getSubmittedByUserId(), "Unknown"), v.getNote(), v.getSubmittedAt(),
                                v.isRemoved(), v.getRemovalReason(),
                                c.getLastApprovedVersion() != null && c.getLastApprovedVersion() == v.getNumber()))
                        .toList(),
                reviewRows.stream()
                        .map(r -> new ReviewView(
                                r.getVersionNumber(), r.getReviewerUserId(), names.getOrDefault(r.getReviewerUserId(), "Unknown"),
                                r.getDecision(), r.getComment(), r.getDecidedAt()))
                        .toList(),
                historyRows.stream()
                        .map(h -> new HistoryLine(
                                h.getEvent(), h.getActorUserId() == null ? "System" : names.getOrDefault(h.getActorUserId(), "Unknown"),
                                h.getAt(), h.getBeforeValue(), h.getAfterValue(), h.getNote()))
                        .toList(),
                new Actions(submit, edit, review, review, remove));
    }

    private boolean anyGranted(Set<Role> roles, PermissionAction action) {
        return roles.stream().anyMatch(r -> matrix.isGranted(r, PermissionModule.CONTENT, action));
    }

    public ContentVersion versionOf(Caller caller, UUID contentId, int number) {
        service.visible(caller, contentId);
        return versions.findByContentIdAndNumber(contentId, number).orElseThrow(() -> new NotFoundException("Version not found."));
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        users.findAllById(ids).forEach(u -> out.put(u.getId(), u.getDisplayName()));
        return out;
    }
}
