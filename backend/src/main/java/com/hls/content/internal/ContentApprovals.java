package com.hls.content.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Submitted content waiting for a decision (inbox kind {@code CONTENT_REVIEW}), excluding what the caller wrote or submitted. */
@Component
class ContentApprovals implements ApprovalSource {

    static final String KIND = "CONTENT_REVIEW";

    private final ContentQueries queries;
    private final ContentVersionRepository versions;
    private final AppUserRepository users;

    ContentApprovals(ContentQueries queries, ContentVersionRepository versions, AppUserRepository users) {
        this.queries = queries;
        this.versions = versions;
        this.users = users;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "CONTENT";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        List<Content> waiting = queries.awaitingReview().stream()
                .filter(c -> !c.getAuthorUserId().equals(userId))
                .filter(c -> versions.findByContentIdAndNumber(c.getId(), c.getCurrentVersion())
                        .map(v -> !v.getSubmittedByUserId().equals(userId))
                        .orElse(true))
                .toList();
        Map<UUID, String> names = users.findAllById(waiting.stream().map(Content::getAuthorUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (a, b) -> a));
        return waiting.stream()
                .map(c -> new ApprovalItem(
                        KIND, c.getId(), c.getAuthorUserId(), names.getOrDefault(c.getAuthorUserId(), "Unknown"),
                        c.getTitle() + " (version " + c.getCurrentVersion() + ")",
                        LocalDate.ofInstant(c.getUpdatedAt(), ZoneOffset.UTC),
                        Link.to("/operations/content/" + c.getId())))
                .toList();
    }
}
