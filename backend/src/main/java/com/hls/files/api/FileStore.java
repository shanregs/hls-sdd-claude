package com.hls.files.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The shared store for uploaded files (spec 023, contract C7). A file is stored once under a generated name and is
 * never changed; a removal hides it and records who, when and why, while the bytes stay on disk so the history is
 * complete. Types are limited to jpg, png, pdf, docx, xlsx and txt (checked by extension and by content), and a file
 * may be at most 10 MB. Callers decide who may see a file; the store does not know about visits or bills.
 */
public interface FileStore {

    long MAX_BYTES = 10L * 1024 * 1024;

    /** The metadata of a stored file; {@code removed} files are not returned by {@link #find} or {@link #list}. */
    record FileRef(
            UUID id,
            String ownerType,
            UUID ownerId,
            String name,
            String contentType,
            long sizeBytes,
            UUID addedBy,
            Instant addedAt) {}

    /** A file with its bytes, ready to be sent as a download. */
    record FileContent(FileRef file, byte[] bytes) {}

    /** Stores the bytes after checking type, content and size; throws an invalid-input error for anything refused. */
    FileRef store(UUID actor, String ownerType, UUID ownerId, String originalName, byte[] content);

    Optional<FileRef> find(UUID fileId);

    /** The files of an owner that have not been removed, oldest first. */
    List<FileRef> list(String ownerType, UUID ownerId);

    Optional<FileContent> open(UUID fileId);

    /** Hides the file and records who removed it and why; the bytes are kept. A reason is required. */
    void remove(UUID actor, UUID fileId, String reason);
}
