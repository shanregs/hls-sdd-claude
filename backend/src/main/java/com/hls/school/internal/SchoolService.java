package com.hls.school.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolChangeGuard;
import com.hls.school.api.SchoolDeactivationGuard;
import com.hls.school.api.SchoolScopeProvider;
import com.hls.school.api.SchoolView;
import com.hls.school.api.SchoolViewEnricher;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School CRUD (spec 005 US2): created in a Place, Zone derived from it, scoped reads for Managers,
 * field-limited edits for non-Admin/Director callers, guarded place changes and deactivation.
 */
@Service
public class SchoolService {

    /** The profile fields a Manager may change (FR-006). */
    public record Profile(
            String name,
            String address,
            String contactPerson,
            String contactPhone,
            String billingContact,
            Long version) {}

    private static final UUID NO_ID = new UUID(0L, 0L);

    private final SchoolRepository schoolRepository;
    private final PlaceRepository placeRepository;
    private final ZoneRepository zoneRepository;
    private final List<SchoolScopeProvider> scopeProviders;
    private final List<SchoolChangeGuard> changeGuards;
    private final List<SchoolDeactivationGuard> deactivationGuards;
    private final List<SchoolViewEnricher> enrichers;
    private final ChangeRecorder changes;
    private final Clock clock;

    public SchoolService(
            SchoolRepository schoolRepository,
            PlaceRepository placeRepository,
            ZoneRepository zoneRepository,
            List<SchoolScopeProvider> scopeProviders,
            List<SchoolChangeGuard> changeGuards,
            List<SchoolDeactivationGuard> deactivationGuards,
            List<SchoolViewEnricher> enrichers,
            ChangeRecorder changes,
            Clock clock) {
        this.schoolRepository = schoolRepository;
        this.placeRepository = placeRepository;
        this.zoneRepository = zoneRepository;
        this.scopeProviders = scopeProviders;
        this.changeGuards = changeGuards;
        this.deactivationGuards = deactivationGuards;
        this.enrichers = enrichers;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SchoolView> list(
            UUID userId,
            Set<Role> roles,
            String query,
            UUID zoneId,
            UUID placeId,
            Boolean active,
            Pageable pageable) {
        Set<UUID> scope = scopeOf(userId, roles);
        if (scope != null && scope.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Page<School> page = schoolRepository.search(
                query == null ? "" : query.trim(),
                zoneId != null,
                zoneId == null ? NO_ID : zoneId,
                placeId != null,
                placeId == null ? NO_ID : placeId,
                active != null,
                active != null && active,
                scope != null,
                scope == null ? Set.of(NO_ID) : scope,
                pageable);
        return viewsOf(page);
    }

    @Transactional(readOnly = true)
    public SchoolView get(UUID userId, Set<Role> roles, UUID id) {
        School school = visible(userId, roles, id);
        return viewsOf(List.of(school)).get(0);
    }

    @Transactional
    public SchoolView create(UUID actor, UUID placeId, Profile profile) {
        if (placeId == null) {
            throw new InvalidInputException("A School must be located in a Place.");
        }
        placeRepository.findById(placeId).orElseThrow(() -> new InvalidInputException("Place not found."));
        School school = new School(
                requireName(profile.name()),
                placeId,
                requireAddress(profile.address()),
                limit(profile.contactPerson(), 120, "Contact person"),
                limit(profile.contactPhone(), 20, "Contact phone"),
                limit(profile.billingContact(), 200, "Billing contact"),
                clock.instant());
        school = schoolRepository.saveAndFlush(school);
        changes.recordLifecycle(actor, "SCHOOL", school.getId(), "created", school.getName());
        return viewsOf(List.of(school)).get(0);
    }

    /**
     * Edits the profile. A caller who is not Admin/Director (a Manager) may change only contact
     * person, contact phone and address, and only for a School in their scope (FR-006).
     */
    @Transactional
    public SchoolView update(UUID actor, Set<Role> roles, UUID id, Profile profile) {
        School school = visible(actor, roles, id);
        StaleVersion.check(School.class, id, school.getVersion(), profile.version());
        boolean full = CallerContext.isOrgWide(roles);
        String newName = requireName(profile.name());
        String newBilling = limit(profile.billingContact(), 200, "Billing contact");
        if (!full
                && (!Objects.equals(newName, school.getName())
                        || !Objects.equals(emptyToNull(newBilling), emptyToNull(school.getBillingContact())))) {
            throw new ForbiddenFieldException("Managers can edit only a School's contact person, phone, and address.");
        }
        String address = requireAddress(profile.address());
        String person = limit(profile.contactPerson(), 120, "Contact person");
        String phone = limit(profile.contactPhone(), 20, "Contact phone");

        changes.record(actor, "SCHOOL", id, "name", school.getName(), newName);
        changes.record(actor, "SCHOOL", id, "address", school.getAddress(), address);
        changes.record(actor, "SCHOOL", id, "contactPerson", school.getContactPerson(), person);
        changes.record(actor, "SCHOOL", id, "contactPhone", school.getContactPhone(), phone);
        changes.record(actor, "SCHOOL", id, "billingContact", school.getBillingContact(), newBilling);
        school.setName(newName);
        school.setAddress(address);
        school.setContactPerson(person);
        school.setContactPhone(phone);
        school.setBillingContact(newBilling);
        school.touch(clock.instant());
        schoolRepository.saveAndFlush(school);
        return viewsOf(List.of(school)).get(0);
    }

    @Transactional
    public SchoolView changePlace(UUID actor, Set<Role> roles, UUID id, UUID newPlaceId, Long version) {
        requireOrgWide(roles);
        School school = schoolRepository.findById(id).orElseThrow(() -> new NotFoundException("School not found."));
        StaleVersion.check(School.class, id, school.getVersion(), version);
        Place newPlace = placeRepository
                .findById(newPlaceId)
                .orElseThrow(() -> new InvalidInputException("Place not found."));
        Place oldPlace = placeRepository.findById(school.getPlaceId()).orElseThrow();
        if (!newPlace.getZoneId().equals(oldPlace.getZoneId())) {
            changeGuards.forEach(guard -> guard.checkPlaceChange(id, newPlace.getZoneId()));
        }
        UUID before = school.getPlaceId();
        school.setPlaceId(newPlaceId);
        school.touch(clock.instant());
        schoolRepository.saveAndFlush(school);
        changes.record(actor, "SCHOOL", id, "place", before, newPlaceId);
        return viewsOf(List.of(school)).get(0);
    }

    @Transactional
    public void deactivate(UUID actor, Set<Role> roles, UUID id) {
        requireOrgWide(roles);
        School school = schoolRepository.findById(id).orElseThrow(() -> new NotFoundException("School not found."));
        if (!school.isActive()) {
            return;
        }
        deactivationGuards.forEach(guard -> guard.checkDeactivate(id));
        school.setActive(false);
        school.touch(clock.instant());
        schoolRepository.saveAndFlush(school);
        changes.record(actor, "SCHOOL", id, "active", true, false);
    }

    @Transactional
    public void reactivate(UUID actor, Set<Role> roles, UUID id) {
        requireOrgWide(roles);
        School school = schoolRepository.findById(id).orElseThrow(() -> new NotFoundException("School not found."));
        if (school.isActive()) {
            return;
        }
        school.setActive(true);
        school.touch(clock.instant());
        schoolRepository.saveAndFlush(school);
        changes.record(actor, "SCHOOL", id, "active", false, true);
    }

    /** The School ids the caller may see, or {@code null} when unrestricted (Admin/Director). */
    private Set<UUID> scopeOf(UUID userId, Set<Role> roles) {
        if (CallerContext.isOrgWide(roles)) {
            return null;
        }
        Set<UUID> visible = new HashSet<>();
        for (SchoolScopeProvider provider : scopeProviders) {
            provider.visibleSchoolIds(userId, roles).ifPresent(visible::addAll);
        }
        return visible;
    }

    private School visible(UUID userId, Set<Role> roles, UUID id) {
        School school = schoolRepository.findById(id).orElseThrow(() -> new NotFoundException("School not found."));
        Set<UUID> scope = scopeOf(userId, roles);
        if (scope != null && !scope.contains(id)) {
            throw new NotFoundException("School not found.");
        }
        return school;
    }

    private static void requireOrgWide(Set<Role> roles) {
        if (!CallerContext.isOrgWide(roles)) {
            throw new AccessDeniedException("Only Admin or Director may do this.");
        }
    }

    private Page<SchoolView> viewsOf(Page<School> page) {
        List<SchoolView> views = viewsOf(page.getContent());
        return new PageImpl<>(views, page.getPageable(), page.getTotalElements());
    }

    private List<SchoolView> viewsOf(Collection<School> schools) {
        Map<UUID, Place> places = placeRepository
                .findAllById(schools.stream().map(School::getPlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Place::getId, p -> p));
        Map<UUID, Zone> zones = zoneRepository
                .findAllById(places.values().stream().map(Place::getZoneId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Zone::getId, z -> z));
        List<UUID> ids = schools.stream().map(School::getId).toList();
        Map<UUID, Map<String, Object>> extras = new HashMap<>();
        for (SchoolViewEnricher enricher : enrichers) {
            enricher.enrich(ids).forEach((id, attrs) -> extras.computeIfAbsent(id, k -> new HashMap<>()).putAll(attrs));
        }
        return schools.stream()
                .map(s -> {
                    Place place = places.get(s.getPlaceId());
                    Zone zone = zones.get(place.getZoneId());
                    return new SchoolView(
                            s.getId(),
                            s.getName(),
                            new SchoolView.PlaceRef(place.getId(), place.getName(), place.getPinCode()),
                            new SchoolView.ZoneRef(zone.getId(), zone.getName()),
                            s.getAddress(),
                            s.getContactPerson(),
                            s.getContactPhone(),
                            s.getBillingContact(),
                            s.isActive(),
                            s.getVersion(),
                            extras.getOrDefault(s.getId(), Map.of()));
                })
                .toList();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("School name is required.");
        }
        return limit(name, 200, "School name");
    }

    private static String requireAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new InvalidInputException("Address is required.");
        }
        return address.trim();
    }

    private static String limit(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
