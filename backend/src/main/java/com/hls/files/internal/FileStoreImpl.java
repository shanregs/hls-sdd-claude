package com.hls.files.internal;

import com.hls.files.api.FileStore;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores file bytes under {@code hls.files.directory} in {@code yyyy/MM/<generated id>} (the client's name is kept
 * only as text), with the metadata and a SHA-256 checksum in {@code stored_file}.
 */
@Service
class FileStoreImpl implements FileStore {

    private static final int MAX_NAME = 255;

    private final StoredFileRepository files;
    private final Clock clock;
    private final Path root;

    FileStoreImpl(StoredFileRepository files, Clock clock, @Value("${hls.files.directory:./data/files}") String directory) {
        this.files = files;
        this.clock = clock;
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public FileRef store(UUID actor, String ownerType, UUID ownerId, String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidInputException("The file is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidInputException("A file can be at most 10 MB.");
        }
        String name = cleanName(originalName);
        String contentType = FileTypePolicy.check(name, content);
        UUID id = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String relative = "%04d/%02d/%s".formatted(now.getYear(), now.getMonthValue(), id);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new InvalidInputException("The file could not be stored.");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the file", e);
        }
        StoredFile saved = files.saveAndFlush(new StoredFile(
                id, ownerType, ownerId, name, contentType, content.length, sha256(content), relative, actor, clock.instant()));
        return ref(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FileRef> find(UUID fileId) {
        return files.findById(fileId).filter(f -> !f.isRemoved()).map(FileStoreImpl::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileRef> list(String ownerType, UUID ownerId) {
        return files.findByOwnerTypeAndOwnerIdAndRemovedAtIsNullOrderByAddedAt(ownerType, ownerId).stream()
                .map(FileStoreImpl::ref)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FileContent> open(UUID fileId) {
        return files.findById(fileId).filter(f -> !f.isRemoved()).map(f -> {
            Path path = root.resolve(f.getStoragePath()).normalize();
            if (!path.startsWith(root)) {
                throw new NotFoundException("File not found.");
            }
            try {
                return new FileContent(ref(f), Files.readAllBytes(path));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the stored file", e);
            }
        });
    }

    @Override
    @Transactional
    public void remove(UUID actor, UUID fileId, String reason) {
        StoredFile file = files.findById(fileId).filter(f -> !f.isRemoved()).orElseThrow(() -> new NotFoundException("File not found."));
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A reason is required to remove a file.");
        }
        String cleaned = reason.trim();
        if (cleaned.length() > 300) {
            throw new InvalidInputException("The reason must be at most 300 characters.");
        }
        file.markRemoved(actor, cleaned, clock.instant());
        files.saveAndFlush(file);
    }

    private static String cleanName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new InvalidInputException("The file needs a name.");
        }
        // keep only the last path segment and drop control characters; the name is only ever shown, never used as a path
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw new InvalidInputException("The file needs a name.");
        }
        return name.length() > MAX_NAME ? name.substring(name.length() - MAX_NAME) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileRef ref(StoredFile f) {
        return new FileRef(f.getId(), f.getOwnerType(), f.getOwnerId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(), f.getAddedBy(), f.getAddedAt());
    }
}
