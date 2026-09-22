package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.school.api.dto.PlaceView;
import com.hls.school.internal.Place;
import com.hls.school.internal.PlaceRepository;
import com.hls.school.internal.PlaceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for FR-001-007 (User Stories 1-3). Repository is mocked;
 * the real HTTP round trip lives in {@code SchoolIntegrationTest}.
 */
class PlaceServiceTest {

    private PlaceRepository placeRepository;
    private Clock clock;
    private PlaceService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PlaceService(placeRepository, clock);
    }

    // ---- User Story 1: addPlace ------------------------------------------------------------

    @Test
    void addPlace_persistsAndReturnsPlaceViewWithGivenZone() {
        UUID zoneId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();

        PlaceView result = service.addPlace(zoneId, "Ambattur", "600053", actingUserId);

        assertThat(result.zoneId()).isEqualTo(zoneId);
        assertThat(result.name()).isEqualTo("Ambattur");
        assertThat(result.pincode()).isEqualTo("600053");
        verify(placeRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getZoneId().equals(zoneId) && p.getName().equals("Ambattur") && p.getPincode().equals("600053")
                        && p.getCreatedBy().equals(actingUserId) && p.getCreatedAt().equals(NOW)));
    }

    @Test
    void addPlace_allowsSameNameUnderDifferentZone_noRejection() {
        UUID zoneA = UUID.randomUUID();
        UUID zoneB = UUID.randomUUID();

        PlaceView first = service.addPlace(zoneA, "Springfield", "600001", UUID.randomUUID());
        PlaceView second = service.addPlace(zoneB, "Springfield", "600002", UUID.randomUUID());

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.zoneId()).isNotEqualTo(second.zoneId());
    }

    @Test
    void addPlace_allowsSamePincodeUnderDifferentZones_bothRecordedIndependently() {
        UUID zoneA = UUID.randomUUID();
        UUID zoneB = UUID.randomUUID();

        PlaceView first = service.addPlace(zoneA, "Ambattur", "600053", UUID.randomUUID());
        PlaceView second = service.addPlace(zoneB, "Neighboring Village", "600053", UUID.randomUUID());

        assertThat(first.pincode()).isEqualTo(second.pincode());
        assertThat(first.zoneId()).isNotEqualTo(second.zoneId());
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    // ---- User Story 2: findByPincode / findByName ------------------------------------------

    @Test
    void findByPincode_returnsEveryMatchingPlaceAcrossZones() {
        UUID zoneA = UUID.randomUUID();
        UUID zoneB = UUID.randomUUID();
        Place placeA = new Place(UUID.randomUUID(), zoneA, "Ambattur", "600053", NOW, UUID.randomUUID());
        Place placeB = new Place(UUID.randomUUID(), zoneB, "Neighboring Village", "600053", NOW, UUID.randomUUID());
        when(placeRepository.findByPincode("600053")).thenReturn(List.of(placeA, placeB));

        List<PlaceView> result = service.findByPincode("600053");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlaceView::zoneId).containsExactlyInAnyOrder(zoneA, zoneB);
    }

    @Test
    void findByPincode_forUnknownPincode_returnsEmptyList() {
        when(placeRepository.findByPincode("999999")).thenReturn(List.of());

        assertThat(service.findByPincode("999999")).isEmpty();
    }

    @Test
    void findByPincode_trimsWhitespaceBeforeQuerying() {
        when(placeRepository.findByPincode("600053")).thenReturn(List.of());

        service.findByPincode("  600053  ");

        verify(placeRepository).findByPincode("600053");
    }

    @Test
    void findByName_isCaseInsensitive() {
        UUID zoneId = UUID.randomUUID();
        Place place = new Place(UUID.randomUUID(), zoneId, "Ambattur", "600053", NOW, UUID.randomUUID());
        when(placeRepository.findByNameIgnoreCase("ambattur")).thenReturn(List.of(place));

        List<PlaceView> result = service.findByName("ambattur");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).zoneId()).isEqualTo(zoneId);
    }

    @Test
    void findByName_forUnknownName_returnsEmptyList() {
        when(placeRepository.findByNameIgnoreCase(eq("Nowhereville"))).thenReturn(List.of());

        assertThat(service.findByName("Nowhereville")).isEmpty();
    }

    // ---- User Story 3: findByZoneId --------------------------------------------------------

    @Test
    void findByZoneId_returnsExactlyThatZonesPlaces() {
        UUID zoneId = UUID.randomUUID();
        Place placeA = new Place(UUID.randomUUID(), zoneId, "Place A", "600001", NOW, UUID.randomUUID());
        Place placeB = new Place(UUID.randomUUID(), zoneId, "Place B", "600002", NOW, UUID.randomUUID());
        when(placeRepository.findByZoneId(zoneId)).thenReturn(List.of(placeA, placeB));

        List<PlaceView> result = service.findByZoneId(zoneId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlaceView::name).containsExactlyInAnyOrder("Place A", "Place B");
    }

    @Test
    void findByZoneId_forZoneWithNoPlaces_returnsEmptyList() {
        UUID zoneId = UUID.randomUUID();
        when(placeRepository.findByZoneId(zoneId)).thenReturn(List.of());

        assertThat(service.findByZoneId(zoneId)).isEmpty();
    }
}
