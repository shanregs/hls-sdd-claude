package com.hls.recruitment.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The reusable College list: name and city (unique), a placement officer and a principal. */
@Service
public class CollegeService {

    public record ContactDto(String name, String phone, String email) {}

    public record CollegeRequest(
            String name, String city, ContactDto placementOfficer, ContactDto principal, Long version) {}

    public record CollegeDto(
            UUID id,
            String name,
            String city,
            ContactDto placementOfficer,
            ContactDto principal,
            long drives,
            Long version) {}

    private final CollegeRepository colleges;
    private final CollegeContactRepository contacts;
    private final CampusDriveRepository drives;
    private final ChangeRecorder changes;
    private final Clock clock;

    public CollegeService(
            CollegeRepository colleges,
            CollegeContactRepository contacts,
            CampusDriveRepository drives,
            ChangeRecorder changes,
            Clock clock) {
        this.colleges = colleges;
        this.contacts = contacts;
        this.drives = drives;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CollegeDto> list(String query) {
        return views(colleges.search(query == null ? "" : query.trim()));
    }

    @Transactional(readOnly = true)
    public CollegeDto get(UUID id) {
        return views(List.of(find(id))).get(0);
    }

    @Transactional
    public CollegeDto create(UUID actor, CollegeRequest request) {
        String name = Texts.required(request.name(), 160, "College name");
        String city = Texts.required(request.city(), 120, "City");
        colleges.findByNameAndCity(name, city).ifPresent(existing -> {
            throw new ConflictException("This college is already in the list.");
        });
        College college = colleges.saveAndFlush(new College(name, city, actor, clock.instant()));
        saveContact(actor, college.getId(), CollegeContact.PLACEMENT_OFFICER, request.placementOfficer());
        saveContact(actor, college.getId(), CollegeContact.PRINCIPAL, request.principal());
        changes.recordLifecycle(actor, "COLLEGE", college.getId(), "created", name + ", " + city);
        return views(List.of(college)).get(0);
    }

    @Transactional
    public CollegeDto update(UUID actor, UUID id, CollegeRequest request) {
        College college = find(id);
        StaleVersion.check(College.class, id, college.getVersion(), request.version());
        String name = Texts.required(request.name(), 160, "College name");
        String city = Texts.required(request.city(), 120, "City");
        colleges.findByNameAndCity(name, city).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Another college has this name and city.");
        });
        changes.record(actor, "COLLEGE", id, "name", college.getName(), name);
        changes.record(actor, "COLLEGE", id, "city", college.getCity(), city);
        college.rename(name, city);
        saveContact(actor, id, CollegeContact.PLACEMENT_OFFICER, request.placementOfficer());
        saveContact(actor, id, CollegeContact.PRINCIPAL, request.principal());
        colleges.saveAndFlush(college);
        return views(List.of(college)).get(0);
    }

    College find(UUID id) {
        return colleges.findById(id).orElseThrow(() -> new NotFoundException("College not found."));
    }

    private void saveContact(UUID actor, UUID collegeId, String role, ContactDto wanted) {
        String name = wanted == null ? null : Texts.clean(wanted.name(), 160, role + " name");
        String phone = wanted == null ? null : Texts.clean(wanted.phone(), 20, role + " phone");
        String email = wanted == null ? null : Texts.email(wanted.email());
        boolean empty = name == null && phone == null && email == null;
        if (!empty && name == null) {
            throw new com.hls.school.api.InvalidInputException("The " + label(role) + " needs a name.");
        }
        CollegeContact existing = contacts.findById(new CollegeContact.Key(collegeId, role)).orElse(null);
        String before = existing == null ? null : existing.getName();
        if (empty) {
            if (existing != null) {
                contacts.delete(existing);
            }
        } else if (existing == null) {
            contacts.save(new CollegeContact(collegeId, role, name, phone, email));
        } else {
            existing.change(name, phone, email);
            contacts.save(existing);
        }
        changes.record(actor, "COLLEGE", collegeId, role.toLowerCase(), before, empty ? null : name);
    }

    private static String label(String role) {
        return CollegeContact.PRINCIPAL.equals(role) ? "principal" : "placement officer";
    }

    private List<CollegeDto> views(Collection<College> rows) {
        List<UUID> ids = rows.stream().map(College::getId).toList();
        Map<UUID, Map<String, ContactDto>> byCollege = new HashMap<>();
        for (CollegeContact c : contacts.findByCollegeIdIn(ids)) {
            byCollege
                    .computeIfAbsent(c.getCollegeId(), k -> new HashMap<>())
                    .put(c.getRole(), new ContactDto(c.getName(), c.getPhone(), c.getEmail()));
        }
        return rows.stream()
                .map(c -> {
                    Map<String, ContactDto> own = byCollege.getOrDefault(c.getId(), Map.of());
                    return new CollegeDto(
                            c.getId(),
                            c.getName(),
                            c.getCity(),
                            own.get(CollegeContact.PLACEMENT_OFFICER),
                            own.get(CollegeContact.PRINCIPAL),
                            drives.findByCollegeId(c.getId()).size(),
                            c.getVersion());
                })
                .toList();
    }
}
