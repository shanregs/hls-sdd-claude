package com.hls.school.internal;

import com.hls.school.api.BulkImportResult;
import com.hls.school.api.BulkImportResult.Outcome;
import com.hls.school.api.BulkImportResult.RowResult;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk-imports Places into a Zone (spec 005 FR-003): rejects an empty or oversize list up front,
 * then classifies each row independently as added, already existing (same name and PIN code in the
 * Zone, or earlier in the same list) or rejected, so one bad row never blocks the rest.
 */
@Service
public class PlaceBulkImportService {

    public static final int MAX_ROWS = 5000;

    public record Row(String name, String pinCode) {}

    private final PlaceRepository placeRepository;
    private final ZoneRepository zoneRepository;
    private final ChangeRecorder changes;
    private final Clock clock;

    public PlaceBulkImportService(
            PlaceRepository placeRepository, ZoneRepository zoneRepository, ChangeRecorder changes, Clock clock) {
        this.placeRepository = placeRepository;
        this.zoneRepository = zoneRepository;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public BulkImportResult importPlaces(UUID actor, UUID zoneId, List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new InvalidInputException("The list is empty. Add at least one row.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new InvalidInputException("The list has more than " + MAX_ROWS + " rows. Split it and import in parts.");
        }
        if (zoneId == null || zoneRepository.findById(zoneId).isEmpty()) {
            throw new InvalidInputException("Zone not found.");
        }
        Set<String> seen = new HashSet<>();
        for (Place existing : placeRepository.findByZoneId(zoneId)) {
            seen.add(key(existing.getName(), existing.getPinCode()));
        }

        List<RowResult> results = new ArrayList<>(rows.size());
        List<Place> toSave = new ArrayList<>();
        int added = 0;
        int existed = 0;
        int rejected = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int number = i + 1;
            String problem = problemWith(row);
            if (problem != null) {
                results.add(new RowResult(number, Outcome.REJECTED, problem));
                rejected++;
                continue;
            }
            String name = row.name().trim();
            String pin = row.pinCode().trim();
            if (!seen.add(key(name, pin))) {
                results.add(new RowResult(number, Outcome.ALREADY_EXISTS, null));
                existed++;
                continue;
            }
            toSave.add(new Place(zoneId, name, pin, clock.instant()));
            results.add(new RowResult(number, Outcome.ADDED, null));
            added++;
        }
        for (Place place : placeRepository.saveAll(toSave)) {
            changes.recordLifecycle(actor, "PLACE", place.getId(), "created", place.getName() + " " + place.getPinCode());
        }
        return new BulkImportResult(added, existed, rejected, results);
    }

    private static String problemWith(Row row) {
        if (row == null || row.name() == null || row.name().isBlank()) {
            return "Place name is required.";
        }
        if (row.name().trim().length() > 160) {
            return "Place name must be at most 160 characters.";
        }
        if (row.pinCode() == null || !PlaceService.PIN_CODE.matcher(row.pinCode().trim()).matches()) {
            return "PIN code must be six digits.";
        }
        return null;
    }

    private static String key(String name, String pinCode) {
        return name.trim().toLowerCase(Locale.ROOT) + "|" + pinCode.trim();
    }
}
