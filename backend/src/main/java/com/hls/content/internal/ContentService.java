package com.hls.content.internal;

import com.hls.content.api.ContentAssigned;
import com.hls.content.api.ContentNoReviewer;
import com.hls.content.api.ContentReviewed;
import com.hls.content.api.ContentSubmitted;
import com.hls.designation.api.BusinessDate;
import com.hls.files.api.FileLimits;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileRef;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of a content piece (spec 029): create, edit, submit numbered versions with a file, approve or return, and
 * remove a file. The server alone decides who may do what; every change writes the content history and Change History.
 */
@Service
public class ContentService {

    private static final long MB = 1024L * 1024;

    /** FR-005: the accepted types and the largest size of each. */
    public static final FileLimits LIMITS = new FileLimits(Map.of(
            "mp4", 500 * MB,
            "mp3", 100 * MB,
            "m4a", 100 * MB,
            "wav", 100 * MB,
            "pdf", 25 * MB,
            "docx", 25 * MB,
            "xlsx", 25 * MB));

    public static final String OWNER_TYPE = "CONTENT_VERSION";

    public record Caller(UUID userId, Set<Role> roles) {
        public boolean orgWide() {
            return CallerContext.isOrgWide(roles);
        }
    }

    public record CreateRequest(String title, String category, String subject, String description, UUID authorUserId, LocalDate dueOn) {}

    public record UpdateRequest(
            String title, String category, String subject, String description, UUID authorUserId, LocalDate dueOn, Long version) {}

    private final ContentRepository contents;
    private final ContentVersionRepository versions;
    private final ContentReviewRepository reviews;
    private final ContentRecorder recorder;
    private final ContentRules rules;
    private final ContentTaskBridge tasks;
    private final FileStore files;
    private final BusinessDate businessDate;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    public ContentService(
            ContentRepository contents,
            ContentVersionRepository versions,
            ContentReviewRepository reviews,
            ContentRecorder recorder,
            ContentRules rules,
            ContentTaskBridge tasks,
            FileStore files,
            BusinessDate businessDate,
            Clock clock,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc) {
        this.contents = contents;
        this.versions = versions;
        this.reviews = reviews;
        this.recorder = recorder;
        this.rules = rules;
        this.tasks = tasks;
        this.files = files;
        this.businessDate = businessDate;
        this.clock = clock;
        this.events = events;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ visibility

    /** True when the caller authored, created or submitted a version of the content, or is Admin or Director. */
    boolean canSee(Caller caller, Content content) {
        return caller.orgWide()
                || content.getAuthorUserId().equals(caller.userId())
                || content.getCreatedByUserId().equals(caller.userId())
                || versions.existsByContentIdAndSubmittedByUserId(content.getId(), caller.userId());
    }

    Content visible(Caller caller, UUID id) {
        Content content = contents.findById(id).orElseThrow(() -> new NotFoundException("Content not found."));
        if (!canSee(caller, content)) {
            throw new NotFoundException("Content not found.");
        }
        return content;
    }

    /** The author, the creator and org-wide users may prepare and submit; others who see it (as earlier submitters) may not. */
    boolean canWrite(Caller caller, Content content) {
        return caller.orgWide() || content.getAuthorUserId().equals(caller.userId()) || content.getCreatedByUserId().equals(caller.userId());
    }

    // ------------------------------------------------------------------ create and edit

    @Transactional
    public UUID create(Caller caller, CreateRequest request) {
        UUID author = request.authorUserId() == null ? caller.userId() : request.authorUserId();
        if (!author.equals(caller.userId()) && !caller.orgWide()) {
            throw new ForbiddenFieldException("You can create content only for yourself.");
        }
        if (!rules.canAuthor(author)) {
            throw new InvalidInputException("The author must be an active user who can create content.");
        }
        String title = required(request.title(), 3, 120, "The title");
        String category = required(request.category(), 1, 60, "The category");
        String subject = required(request.subject(), 1, 80, "The subject");
        String description = optional(request.description(), 2000, "The description");
        boolean forSomeoneElse = !author.equals(caller.userId());
        UUID id = UUID.randomUUID();
        Long seq = jdbc.queryForObject("select nextval('content_code_seq')", Long.class);
        Content content = new Content(
                id,
                "CNT-%06d".formatted(seq),
                title,
                category,
                subject,
                description,
                author,
                caller.userId(),
                request.dueOn(),
                forSomeoneElse ? ContentStatus.ASSIGNED : ContentStatus.DRAFT,
                clock.instant());
        contents.saveAndFlush(content);
        UUID taskId = tasks.ensureTask(content, caller.userId());
        if (taskId != null) {
            content.linkTask(taskId);
        }
        recorder.created(content, caller.userId(), forSomeoneElse ? "Created for another author" : "Created by the author");
        if (forSomeoneElse) {
            events.publishEvent(new ContentAssigned(id, title, author, caller.userId(), request.dueOn()));
        }
        return id;
    }

    @Transactional
    public void update(Caller caller, UUID id, UpdateRequest request) {
        Content content = visible(caller, id);
        if (!canWrite(caller, content)) {
            throw new ForbiddenFieldException("Only the author, the creator, an Admin or a Director can change this content.");
        }
        StaleVersion.check(Content.class, id, content.getVersion(), request.version());
        UUID author = request.authorUserId() == null ? content.getAuthorUserId() : request.authorUserId();
        if (!author.equals(content.getAuthorUserId())) {
            if (!caller.orgWide()) {
                throw new ForbiddenFieldException("Only an Admin or a Director can change the author.");
            }
            if (!rules.canAuthor(author)) {
                throw new InvalidInputException("The author must be an active user who can create content.");
            }
        }
        LocalDate due = request.dueOn();
        if (!Objects.equals(due, content.getDueOn()) && !caller.orgWide() && !content.getCreatedByUserId().equals(caller.userId())) {
            throw new ForbiddenFieldException("Only the creator, an Admin or a Director can change the due date.");
        }
        String title = required(request.title(), 3, 120, "The title");
        String category = required(request.category(), 1, 60, "The category");
        String subject = required(request.subject(), 1, 80, "The subject");
        String description = optional(request.description(), 2000, "The description");
        UUID actor = caller.userId();
        recorder.change(content, "EDITED", actor, "title", content.getTitle(), title, null);
        recorder.change(content, "EDITED", actor, "category", content.getCategory(), category, null);
        recorder.change(content, "EDITED", actor, "subject", content.getSubject(), subject, null);
        recorder.change(content, "EDITED", actor, "description", content.getDescription(), description, null);
        boolean authorChanged = !author.equals(content.getAuthorUserId());
        boolean dueChanged = !Objects.equals(due, content.getDueOn());
        if (authorChanged) {
            recorder.change(content, "AUTHOR_CHANGED", actor, "authorUserId", content.getAuthorUserId(), author, null);
        }
        if (dueChanged) {
            recorder.change(content, "DUE_CHANGED", actor, "dueOn", content.getDueOn(), due, null);
        }
        content.edit(title, category, subject, description, author, due, clock.instant());
        if (authorChanged || dueChanged) {
            content.linkTask(tasks.resync(content, actor));
            if (authorChanged) {
                events.publishEvent(new ContentAssigned(id, title, author, actor, due));
            }
        }
        contents.saveAndFlush(content);
    }

    // ------------------------------------------------------------------ submit a version

    /** Stores the file and adds the next numbered version; returns that number. The file is required. */
    @Transactional
    public int submit(Caller caller, UUID id, String fileName, InputStream bytes, long declaredSize, String note) {
        Content content = visible(caller, id);
        if (!canWrite(caller, content)) {
            throw new ForbiddenFieldException("Only the author, the creator, an Admin or a Director can submit a version.");
        }
        if (!ContentStatus.OPEN_FOR_SUBMISSION.contains(content.getStatus())) {
            throw new ConflictException("This content is already waiting for review.");
        }
        if (bytes == null || fileName == null || fileName.isBlank()) {
            throw new InvalidInputException("Attach the content file. Content cannot be submitted without it.");
        }
        String text = optional(note, 500, "The note");
        int number = content.getCurrentVersion() + 1;
        FileRef file = files.store(caller.userId(), OWNER_TYPE, content.getId(), fileName, bytes, declaredSize, LIMITS);
        versions.saveAndFlush(new ContentVersion(
                content.getId(),
                number,
                file.id(),
                file.name(),
                file.contentType(),
                file.sizeBytes(),
                content.getAuthorUserId(),
                caller.userId(),
                text,
                clock.instant()));
        ContentStatus before = content.getStatus();
        content.submitted(number, clock.instant());
        recorder.change(content, "SUBMITTED", caller.userId(), "status", before, ContentStatus.SUBMITTED, "Version " + number + ": " + file.name());
        recorder.note(content, "VERSION_ADDED", caller.userId(), "CONTENT_VERSION", content.getId() + "/" + number, file.name());
        tasks.submitted(content, number, file.name(), caller.userId());
        contents.saveAndFlush(content);
        events.publishEvent(new ContentSubmitted(id, content.getTitle(), number, content.getAuthorUserId(), caller.userId()));
        if (rules.eligibleReviewers(content.getAuthorUserId(), caller.userId()).isEmpty()
                && !Objects.equals(content.getNoReviewerNotifiedVersion(), number)) {
            content.noReviewerNoticeSent(number);
            contents.saveAndFlush(content);
            events.publishEvent(new ContentNoReviewer(id, content.getTitle(), number));
        }
        return number;
    }

    // ------------------------------------------------------------------ review

    @Transactional
    public void approve(Caller caller, UUID id) {
        Content content = reviewable(caller, id);
        ContentVersion current = versions.findByContentIdAndNumber(id, content.getCurrentVersion()).orElseThrow();
        ContentStatus before = content.getStatus();
        content.approved(caller.userId(), businessDate.today(), clock.instant());
        reviews.save(new ContentReview(id, current.getNumber(), caller.userId(), "APPROVED", null, clock.instant()));
        recorder.change(content, "APPROVED", caller.userId(), "status", before, ContentStatus.APPROVED, "Version " + current.getNumber());
        tasks.approved(content, caller.userId());
        contents.saveAndFlush(content);
        events.publishEvent(new ContentReviewed(
                id, content.getTitle(), current.getNumber(), "APPROVED", null, content.getAuthorUserId(), current.getSubmittedByUserId(), caller.userId()));
    }

    @Transactional
    public void returnForRevision(Caller caller, UUID id, String comment) {
        String text = required(comment, 3, 1000, "A comment");
        Content content = reviewable(caller, id);
        ContentVersion current = versions.findByContentIdAndNumber(id, content.getCurrentVersion()).orElseThrow();
        ContentStatus before = content.getStatus();
        content.returned(caller.userId(), clock.instant());
        reviews.save(new ContentReview(id, current.getNumber(), caller.userId(), "RETURNED", text, clock.instant()));
        recorder.change(content, "RETURNED", caller.userId(), "status", before, ContentStatus.RETURNED, text);
        tasks.returned(content, text, caller.userId());
        contents.saveAndFlush(content);
        events.publishEvent(new ContentReviewed(
                id, content.getTitle(), current.getNumber(), "RETURNED", text, content.getAuthorUserId(), current.getSubmittedByUserId(), caller.userId()));
    }

    /** Submitted content the caller may decide: not their own as author, creator-submitter or submitter of the current version. */
    private Content reviewable(Caller caller, UUID id) {
        Content content = visible(caller, id);
        if (content.getStatus() != ContentStatus.SUBMITTED) {
            throw new ConflictException("Only submitted content can be reviewed. It is " + content.getStatus().label().toLowerCase() + ".");
        }
        ContentVersion current = versions.findByContentIdAndNumber(id, content.getCurrentVersion()).orElseThrow();
        if (caller.userId().equals(content.getAuthorUserId()) || caller.userId().equals(current.getSubmittedByUserId())) {
            throw new ForbiddenFieldException("You cannot review content you wrote or submitted.");
        }
        return content;
    }

    boolean canReview(Caller caller, Content content, ContentVersion current) {
        return content.getStatus() == ContentStatus.SUBMITTED
                && current != null
                && !caller.userId().equals(content.getAuthorUserId())
                && !caller.userId().equals(current.getSubmittedByUserId());
    }

    // ------------------------------------------------------------------ file removal

    @Transactional
    public void removeFile(Caller caller, UUID id, int number, String reason) {
        Content content = visible(caller, id);
        if (!caller.orgWide()) {
            throw new ForbiddenFieldException("Only an Admin or a Director can remove a file.");
        }
        String text = required(reason, 3, 300, "The reason");
        ContentVersion version = versions.findByContentIdAndNumber(id, number).orElseThrow(() -> new NotFoundException("Version not found."));
        if (version.isRemoved()) {
            throw new ConflictException("This file was already removed.");
        }
        files.remove(caller.userId(), version.getFileId(), text);
        version.markRemoved(caller.userId(), text, clock.instant());
        versions.saveAndFlush(version);
        recorder.note(content, "FILE_REMOVED", caller.userId(), "CONTENT_VERSION", id + "/" + number, "Version " + number + ": " + text);
        content.touch(clock.instant());
        contents.saveAndFlush(content);
    }

    // ------------------------------------------------------------------ text rules

    private static String required(String raw, int min, int max, String what) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < min) {
            throw new InvalidInputException(what + " is required (at least " + min + " characters).");
        }
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }

    private static String optional(String raw, int max, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }
}
