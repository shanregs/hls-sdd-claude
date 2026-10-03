package com.hls.school.internal;

import com.hls.school.api.SchoolDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only directory other modules use to look up Zones, Places and Schools. */
@Service
@Transactional(readOnly = true)
public class SchoolDirectoryImpl implements SchoolDirectory {

    private final ZoneRepository zoneRepository;
    private final PlaceRepository placeRepository;
    private final SchoolRepository schoolRepository;

    public SchoolDirectoryImpl(
            ZoneRepository zoneRepository, PlaceRepository placeRepository, SchoolRepository schoolRepository) {
        this.zoneRepository = zoneRepository;
        this.placeRepository = placeRepository;
        this.schoolRepository = schoolRepository;
    }

    @Override
    public Optional<ZoneInfo> zone(UUID zoneId) {
        return zoneRepository.findById(zoneId).map(z -> new ZoneInfo(z.getId(), z.getName()));
    }

    @Override
    public Optional<ZoneInfo> zoneByName(String name) {
        return zoneRepository.findByNameIgnoreCase(name).map(z -> new ZoneInfo(z.getId(), z.getName()));
    }

    @Override
    public List<ZoneInfo> zones(Collection<UUID> zoneIds) {
        return zoneRepository.findAllById(zoneIds).stream()
                .map(z -> new ZoneInfo(z.getId(), z.getName()))
                .toList();
    }

    @Override
    public Optional<PlaceInfo> place(UUID placeId) {
        return placeRepository
                .findById(placeId)
                .map(p -> new PlaceInfo(p.getId(), p.getName(), p.getPinCode(), p.getZoneId()));
    }

    @Override
    public Optional<SchoolInfo> school(UUID schoolId) {
        return schoolRepository.findById(schoolId).map(this::info);
    }

    @Override
    public List<SchoolInfo> schools(Collection<UUID> schoolIds) {
        List<School> schools = schoolRepository.findAllById(schoolIds);
        Map<UUID, UUID> zoneByPlace = zoneByPlace(schools);
        return schools.stream().map(s -> info(s, zoneByPlace.get(s.getPlaceId()))).toList();
    }

    @Override
    public List<SchoolInfo> schoolsInZone(UUID zoneId) {
        return schoolRepository.findInZone(zoneId).stream()
                .map(s -> info(s, zoneId))
                .toList();
    }

    private SchoolInfo info(School school) {
        UUID zoneId = placeRepository
                .findById(school.getPlaceId())
                .map(Place::getZoneId)
                .orElseThrow();
        return info(school, zoneId);
    }

    private static SchoolInfo info(School school, UUID zoneId) {
        return new SchoolInfo(school.getId(), school.getName(), school.getPlaceId(), zoneId, school.isActive());
    }

    private Map<UUID, UUID> zoneByPlace(List<School> schools) {
        return placeRepository
                .findAllById(schools.stream().map(School::getPlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Place::getId, Place::getZoneId, (a, b) -> a, java.util.HashMap::new));
    }
}
