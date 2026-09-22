package com.hls.school.internal;

import com.hls.school.api.BulkImportBatchException;
import com.hls.school.api.PlaceCommands;
import com.hls.school.api.PlaceQueries;
import com.hls.school.api.dto.BulkImportResponse;
import com.hls.school.api.dto.BulkImportRowResult;
import com.hls.school.api.dto.BulkPlaceRow;
import com.hls.school.api.dto.PlaceView;
import java.time.Clock;
import java.util.ArrayList;
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

    private static final int MAX_BULK_IMPORT_ROWS = 5000;

    private final PlaceRepository placeRepository;
    private final ZoneRepository zoneRepository;
    private final Clock clock;

    public PlaceService(PlaceRepository placeRepository, ZoneRepository zoneRepository, Clock clock) {
        this.placeRepository = placeRepository;
        this.zoneRepository = zoneRepository;
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

    @Override
    @Transactional
    public BulkImportResponse bulkImportPlaces(List<BulkPlaceRow> rows, UUID actingUserId) {
        if (rows.isEmpty()) {
            throw new BulkImportBatchException("batch must not be empty");
        }
        if (rows.size() > MAX_BULK_IMPORT_ROWS) {
            throw new BulkImportBatchException("batch exceeds the maximum of " + MAX_BULK_IMPORT_ROWS + " rows");
        }

        List<BulkImportRowResult> results = new ArrayList<>(rows.size());
        int successCount = 0;
        int failureCount = 0;

        for (int index = 0; index < rows.size(); index++) {
            BulkPlaceRow row = rows.get(index);
            String failureReason = validateRow(row);
            if (failureReason != null) {
                results.add(BulkImportRowResult.failure(index, failureReason));
                failureCount++;
                continue;
            }
            Place place = new Place(UUID.randomUUID(), row.zoneId(), row.name(), row.pincode(), clock.instant(), actingUserId);
            placeRepository.save(place);
            results.add(BulkImportRowResult.success(index, toView(place)));
            successCount++;
        }

        return new BulkImportResponse(results, successCount, failureCount);
    }

    /** @return a failure reason, or null if the row is valid (FR-004). */
    private String validateRow(BulkPlaceRow row) {
        if (row.name() == null || row.name().isBlank()) {
            return "name is required";
        }
        if (row.pincode() == null || row.pincode().isBlank()) {
            return "pincode is required";
        }
        if (row.zoneId() == null || zoneRepository.findById(row.zoneId()).isEmpty()) {
            return "zone " + row.zoneId() + " does not exist";
        }
        return null;
    }

    private PlaceView toView(Place place) {
        return new PlaceView(place.getId(), place.getZoneId(), place.getName(), place.getPincode());
    }
}
