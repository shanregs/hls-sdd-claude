package com.hls.school.internal;

import com.hls.school.api.SchoolRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SchoolRegistryImpl implements SchoolRegistry {

    private final SchoolService schools;
    private final SchoolRepository repository;

    SchoolRegistryImpl(SchoolService schools, SchoolRepository repository) {
        this.schools = schools;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findByNameInPlace(UUID placeId, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return repository.findByPlaceId(placeId).stream()
                .filter(s -> s.getName().equalsIgnoreCase(name.trim()))
                .map(School::getId)
                .findFirst();
    }

    @Override
    @Transactional
    public UUID create(UUID actor, UUID placeId, NewSchool school) {
        return schools
                .create(
                        actor,
                        placeId,
                        new SchoolService.Profile(
                                school.name(),
                                school.address(),
                                school.contactPerson(),
                                school.contactPhone(),
                                school.billingContact(),
                                null))
                .id();
    }
}
