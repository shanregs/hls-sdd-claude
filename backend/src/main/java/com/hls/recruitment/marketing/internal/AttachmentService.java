package com.hls.recruitment.marketing.internal;

import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photos and documents on a visit, kept in the shared file store. Anyone who can see the visit can add up to ten files
 * and download them; only Admin and Director remove one, with a reason (the bytes are kept).
 */
@Service
public class AttachmentService {

    static final int MAX_FILES = 10;

    private final ActivityService activities;
    private final ActivityAttachmentRepository links;
    private final FileStore files;
    private final ChangeRecorder changes;
    private final Clock clock;

    public AttachmentService(
            ActivityService activities, ActivityAttachmentRepository links, FileStore files, ChangeRecorder changes, Clock clock) {
        this.activities = activities;
        this.links = links;
        this.files = files;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public ActivityService.AttachmentRef upload(UUID actor, Set<Role> roles, UUID activityId, String name, byte[] content) {
        activities.visible(actor, roles, activityId);
        if (activities.attachmentsOf(activityId).size() >= MAX_FILES) {
            throw new ConflictException("A visit can have at most " + MAX_FILES + " files.");
        }
        FileStore.FileRef stored = files.store(actor, ActivityService.FILE_OWNER, activityId, name, content);
        links.save(new ActivityAttachment(activityId, stored.id(), actor, clock.instant()));
        changes.recordLifecycle(actor, "ACTIVITY_ATTACHMENT", activityId, "added", stored.name() + " (" + stored.sizeBytes() + " bytes)");
        return new ActivityService.AttachmentRef(stored.id(), stored.name(), stored.sizeBytes(), stored.contentType());
    }

    /** The file, if the caller can see the visit it belongs to; anyone else gets not found. */
    @Transactional(readOnly = true)
    public FileContent download(UUID userId, Set<Role> roles, UUID fileId) {
        ActivityAttachment link = links.findByFileId(fileId).orElseThrow(() -> new NotFoundException("File not found."));
        activities.visible(userId, roles, link.getActivityId());
        return files.open(fileId).orElseThrow(() -> new NotFoundException("File not found."));
    }

    @Transactional
    public void remove(UUID actor, Set<Role> roles, UUID activityId, UUID fileId, String reason) {
        if (!CallerContext.isOrgWide(roles)) {
            throw new ForbiddenFieldException("Only Admin or Director can remove an attachment.");
        }
        activities.visible(actor, roles, activityId);
        ActivityAttachment link = links.findByFileId(fileId)
                .filter(l -> l.getActivityId().equals(activityId))
                .orElseThrow(() -> new NotFoundException("File not found."));
        String name = files.find(link.getFileId()).map(FileStore.FileRef::name).orElse("file");
        files.remove(actor, fileId, reason);
        changes.record(actor, "ACTIVITY_ATTACHMENT", activityId, "removed", name, "removed: " + reason.trim());
    }
}
