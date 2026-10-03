package com.hls.school.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PlaceView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Places of a Zone: add, edit, delete, list and lookup (spec 005 FR-002, FR-005a). */
@Service
public class PlaceService {

    static final Pattern PIN_CODE = Pattern.compile("^[0-9]{6}$");

    private final PlaceRepository placeRepository;
    private final ZoneRepository zoneRepository;
    private final SchoolRepository schoolRepository;
    private final ChangeRecorder changes;
    private final Clock clock;

    public PlaceService(
            PlaceRepository placeRepository,
            ZoneRepository zoneRepository,
            SchoolRepository schoolRepository,
            ChangeRecorder changes,
            Clock clock) {
        this.placeRepository = placeRepository;
        this.zoneRepository = zoneRepository;
        this.schoolRepository = schoolRepository;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PlaceView> listInZone(UUID zoneId, String query, Pageable pageable) {
        Zone zone = requireZone(zoneId);
        String term = query == null ? "" : query.trim();
        return placeRepository.searchInZone(zoneId, term, pageable).map(p -> view(p, zone.getName()));
    }

    /** Every Place matching the PIN code and/or name (names and PIN codes are not unique). */
    @Transactional(readOnly = true)
    public List<PlaceView> lookup(String pinCode, String name) {
        if ((pinCode == null || pinCode.isBlank()) && (name == null || name.isBlank())) {
            throw new InvalidInputException("Give a PIN code or a name to look up.");
        }
        List<Place> matches = new ArrayList<>();
        if (pinCode != null && !pinCode.isBlank()) {
            matches.addAll(placeRepository.findByPinCode(pinCode.trim()));
            if (name != null && !name.isBlank()) {
                String lower = name.trim().toLowerCase(java.util.Locale.ROOT);
                matches.removeIf(p -> !p.getName().toLowerCase(java.util.Locale.ROOT).contains(lower));
            }
        } else {
            matches.addAll(placeRepository.findByNameContainingIgnoreCase(name.trim()));
        }
        Map<UUID, String> zoneNames = zoneNames(matches);
        return matches.stream().map(p -> view(p, zoneNames.get(p.getZoneId()))).toList();
    }

    @Transactional
    public PlaceView add(UUID actor, UUID zoneId, String name, String pinCode) {
        Zone zone = requireZone(zoneId);
        Place place = placeRepository.saveAndFlush(
                new Place(zoneId, requireName(name), requirePin(pinCode), clock.instant()));
        changes.recordLifecycle(actor, "PLACE", place.getId(), "created", place.getName() + " " + place.getPinCode());
        return view(place, zone.getName());
    }

    @Transactional
    public PlaceView edit(UUID actor, UUID id, String name, String pinCode, UUID zoneId) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new NotFoundException("Place not found."));
        String newName = requireName(name);
        String newPin = requirePin(pinCode);
        UUID newZoneId = zoneId == null ? place.getZoneId() : zoneId;
        Zone zone = requireZone(newZoneId);
        if (!newZoneId.equals(place.getZoneId()) && schoolRepository.existsByPlaceId(id)) {
            throw new ConflictException("This Place cannot move to another Zone while Schools are located in it.");
        }
        String beforeName = place.getName();
        String beforePin = place.getPinCode();
        UUID beforeZone = place.getZoneId();
        place.update(newName, newPin, newZoneId);
        placeRepository.saveAndFlush(place);
        changes.record(actor, "PLACE", id, "name", beforeName, newName);
        changes.record(actor, "PLACE", id, "pinCode", beforePin, newPin);
        changes.record(actor, "PLACE", id, "zone", beforeZone, newZoneId);
        return view(place, zone.getName());
    }

    @Transactional
    public void delete(UUID actor, UUID id) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new NotFoundException("Place not found."));
        if (schoolRepository.existsByPlaceId(id)) {
            throw new ConflictException("This Place cannot be deleted while Schools are located in it.");
        }
        placeRepository.delete(place);
        changes.recordLifecycle(actor, "PLACE", id, "deleted", place.getName());
    }

    static String requirePin(String pinCode) {
        if (pinCode == null || !PIN_CODE.matcher(pinCode.trim()).matches()) {
            throw new InvalidInputException("PIN code must be six digits.");
        }
        return pinCode.trim();
    }

    static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Place name is required.");
        }
        if (name.trim().length() > 160) {
            throw new InvalidInputException("Place name must be at most 160 characters.");
        }
        return name.trim();
    }

    private Zone requireZone(UUID zoneId) {
        return zoneRepository.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone not found."));
    }

    private Map<UUID, String> zoneNames(List<Place> places) {
        return zoneRepository
                .findAllById(places.stream().map(Place::getZoneId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Zone::getId, Zone::getName));
    }

    private static PlaceView view(Place place, String zoneName) {
        return new PlaceView(place.getId(), place.getName(), place.getPinCode(), place.getZoneId(), zoneName);
    }
}
