package com.hls.audit.internal;

import com.hls.audit.api.AuditReader;
import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditEntryView;
import com.hls.audit.api.dto.AuditRecordRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements both {@link AuditWriter} and {@link AuditReader} — one class,
 * since both operations share the same {@link AuditEntryRepository} and
 * neither has enough independent logic to warrant splitting (mirrors
 * {@code organization.internal.AccountabilityService}'s reasoning for its
 * own query+command interfaces).
 */
@Service
public class AuditService implements AuditWriter, AuditReader {

    private final AuditEntryRepository repository;
    private final Clock clock;

    public AuditService(AuditEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public UUID record(AuditRecordRequest request) {
        if (request.actorUserId() == null) {
            throw new IllegalArgumentException("An audit entry must be attributed to an actor (FR-010)");
        }
        UUID id = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        AuditEntry entry = new AuditEntry(
                id,
                request.sourceModule(),
                request.entityType(),
                request.entityId(),
                request.action(),
                request.summary(),
                request.beforeValue(),
                request.afterValue(),
                request.actorUserId(),
                request.actorRole(),
                occurredAt,
                request.requestId());
        // FR-004: runs inside whatever transaction the caller is already in
        // (default Spring @Transactional propagation) — research.md §3.
        repository.save(entry);
        return id;
    }

    @Override
    public List<AuditEntryView> history(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderBySequenceNoAsc(entityType, entityId).stream()
                .map(AuditService::toView)
                .toList();
    }

    private static AuditEntryView toView(AuditEntry entry) {
        return new AuditEntryView(
                entry.getId(),
                entry.getSequenceNo(),
                entry.getSourceModule(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getAction(),
                entry.getSummary(),
                entry.getBeforeValue(),
                entry.getAfterValue(),
                entry.getActorUserId(),
                entry.getActorRole(),
                entry.getOccurredAt(),
                entry.getRequestId());
    }
}
