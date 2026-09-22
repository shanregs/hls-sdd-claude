package com.hls.school.internal;

import com.hls.school.api.PlaceCommands;
import com.hls.school.api.PlaceQueries;
import com.hls.school.api.dto.PlaceView;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements both {@link PlaceQueries} and {@link PlaceCommands} — one
 * class, mirroring {@code ZoneService}'s own reasoning.
 */
@Service
public class PlaceService implements PlaceQueries, PlaceCommands {

    private final PlaceRepository placeRepository;
    private final Clock clock;

    public PlaceService(PlaceRepository placeRepository, Clock clock) {
        this.placeRepository = placeRepository;
        this.clock = clock;
    }

    // ---- Queries -----------------------------------------------------------------------

    @Override
    public List<PlaceView> findByPincode(String pincode) {
        return placeRepository.findByPincode(pincode.trim()).stream().map(this::toView).toList();
    }

    @Override
    public List<PlaceView> findByName(String name) {
        return placeRepository.findByNameIgnoreCase(name.trim()).stream().map(this::toView).toList();
    }

    @Override
    public List<PlaceView> findByZoneId(UUID zoneId) {
        return placeRepository.findByZoneId(zoneId).stream().map(this::toView).toList();
    }

    // ---- Commands ----------------------------------------------------------------------

    @Override
    @Transactional
    public PlaceView addPlace(UUID zoneId, String name, String pincode, UUID actingUserId) {
        Place place = new Place(UUID.randomUUID(), zoneId, name, pincode, clock.instant(), actingUserId);
        placeRepository.save(place);
        return toView(place);
    }

    private PlaceView toView(Place place) {
        return new PlaceView(place.getId(), place.getZoneId(), place.getName(), place.getPincode());
    }
}
