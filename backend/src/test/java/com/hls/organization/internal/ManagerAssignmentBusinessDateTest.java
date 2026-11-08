package com.hls.organization.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.UserAdminService;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Regression: Manager assignment dates (which feed scope) use the IST date; 00:30 IST is still the previous UTC day. */
class ManagerAssignmentBusinessDateTest {

    @Test
    void schoolManagerAssignmentStartsAndEndsOnTheIstDate() {
        // 2026-11-07T19:00Z is 2026-11-08 00:30 IST.
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        UUID school = UUID.randomUUID();
        UUID zone = UUID.randomUUID();
        UUID oldManager = UUID.randomUUID();
        UUID newManager = UUID.randomUUID();

        ManagerRepository managers = mock(ManagerRepository.class);
        ZoneManagerAssignmentRepository zoneAssignments = mock(ZoneManagerAssignmentRepository.class);
        SchoolManagerAssignmentRepository schoolAssignments = mock(SchoolManagerAssignmentRepository.class);
        SchoolDirectory directory = mock(SchoolDirectory.class);
        Manager active = mock(Manager.class);
        when(active.isActive()).thenReturn(true);
        when(managers.lockAll(any())).thenReturn(List.of(active, active));
        when(managers.findById(newManager)).thenReturn(Optional.of(active));
        when(directory.school(school)).thenReturn(Optional.of(new SchoolInfo(school, "School", null, zone, true)));
        when(zoneAssignments.existsByZoneIdAndManagerIdAndEndsOnIsNull(zone, newManager)).thenReturn(true);
        SchoolManagerAssignment oldRow = new SchoolManagerAssignment(school, oldManager, LocalDate.parse("2026-10-01"));
        when(schoolAssignments.findBySchoolIdAndEndsOnIsNull(school)).thenReturn(Optional.of(oldRow));

        ManagerService service = new ManagerService(
                managers, zoneAssignments, schoolAssignments, directory, mock(UserAdminService.class), List.of(),
                mock(ManagerEmploymentService.class), mock(ChangeRecorder.class), clock,
                new BusinessDate(clock, "Asia/Kolkata"));

        service.assignSchoolManager(UUID.randomUUID(), school, newManager);

        assertThat(oldRow.getEndsOn()).isEqualTo(LocalDate.parse("2026-11-08"));
        ArgumentCaptor<SchoolManagerAssignment> saved = ArgumentCaptor.forClass(SchoolManagerAssignment.class);
        verify(schoolAssignments).save(saved.capture());
        assertThat(saved.getValue().getStartsOn()).isEqualTo(LocalDate.parse("2026-11-08"));
    }
}
