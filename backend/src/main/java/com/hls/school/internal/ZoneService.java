package com.hls.school.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.school.api.ZoneChangeGuard;
import com.hls.school.api.ZoneView;
import com.hls.school.api.ZoneViewEnricher;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Zone CRUD with audit and the delete guards (spec 005 FR-001). */
@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final PlaceRepository placeRepository;
    private final SchoolRepository schoolRepository;
    private final List<ZoneChangeGuard> deleteGuards;
    private final List<ZoneViewEnricher> enrichers;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ZoneService(
            ZoneRepository zoneRepository,
            PlaceRepository placeRepository,
            SchoolRepository schoolRepository,
            List<ZoneChangeGuard> deleteGuards,
            List<ZoneViewEnricher> enrichers,
            ChangeRecorder changes,
            Clock clock) {
        this.zoneRepository = zoneRepository;
        this.placeRepository = placeRepository;
        this.schoolRepository = schoolRepository;
        this.deleteGuards = deleteGuards;
        this.enrichers = enrichers;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ZoneView> list(String query, Pageable pageable) {
        String term = query == null ? "" : query.trim();
        Page<Zone> page = zoneRepository.findByNameContainingIgnoreCase(term, pageable);
        List<UUID> ids = page.getContent().stream().map(Zone::getId).toList();
        Map<UUID, Map<String, Object>> extras = new HashMap<>();
        for (ZoneViewEnricher enricher : enrichers) {
            enricher.enrich(ids).forEach((id, attrs) -> extras.computeIfAbsent(id, k -> new HashMap<>()).putAll(attrs));
        }
        return page.map(z -> view(z, extras.getOrDefault(z.getId(), Map.of())));
    }

    @Transactional(readOnly = true)
    public ZoneView get(UUID id) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found."));
        Map<String, Object> extras = new HashMap<>();
        for (ZoneViewEnricher enricher : enrichers) {
            Map<String, Object> attrs = enricher.enrich(List.of(id)).get(id);
            if (attrs != null) {
                extras.putAll(attrs);
            }
        }
        return view(zone, extras);
    }

    @Transactional
    public ZoneView create(UUID actor, String name) {
        String clean = requireName(name);
        if (zoneRepository.findByNameIgnoreCase(clean).isPresent()) {
            throw new ConflictException("A Zone named " + clean + " already exists.");
        }
        Zone zone = zoneRepository.saveAndFlush(new Zone(clean, clock.instant()));
        changes.recordLifecycle(actor, "ZONE", zone.getId(), "created", clean);
        return view(zone, Map.of());
    }

    @Transactional
    public ZoneView rename(UUID actor, UUID id, String name, Long version) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found."));
        StaleVersion.check(Zone.class, id, zone.getVersion(), version);
        String clean = requireName(name);
        zoneRepository
                .findByNameIgnoreCase(clean)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("A Zone named " + clean + " already exists.");
                });
        String before = zone.getName();
        zone.rename(clean, clock.instant());
        zoneRepository.saveAndFlush(zone);
        changes.record(actor, "ZONE", id, "name", before, clean);
        return view(zone, Map.of());
    }

    @Transactional
    public void delete(UUID actor, UUID id) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found."));
        long places = placeRepository.countByZoneId(id);
        long schools = schoolRepository.countInZone(id);
        if (places > 0 || schools > 0) {
            throw new ConflictException("This Zone cannot be deleted: it still has " + places + " Place(s) and "
                    + schools + " School(s).");
        }
        deleteGuards.forEach(guard -> guard.checkDelete(id));
        zoneRepository.delete(zone);
        changes.recordLifecycle(actor, "ZONE", id, "deleted", zone.getName());
    }

    private ZoneView view(Zone zone, Map<String, Object> extras) {
        return new ZoneView(
                zone.getId(),
                zone.getName(),
                zone.getVersion(),
                placeRepository.countByZoneId(zone.getId()),
                schoolRepository.countInZone(zone.getId()),
                extras);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Zone name is required.");
        }
        if (name.trim().length() > 120) {
            throw new InvalidInputException("Zone name must be at most 120 characters.");
        }
        return name.trim();
    }
}
