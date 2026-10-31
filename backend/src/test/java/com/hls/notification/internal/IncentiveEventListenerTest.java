package com.hls.notification.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.incentive.api.IncentiveEarned;
import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.SchoolDirectory;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 030: a new incentive tells every Director and every eligible decider exactly once (no Spring context). */
class IncentiveEventListenerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final RecipientResolver recipients = mock(RecipientResolver.class);
    private final ManagerQueries managers = mock(ManagerQueries.class);
    private final SchoolDirectory schools = mock(SchoolDirectory.class);
    private final IncentiveEventListener listener = new IncentiveEventListener(notifications, recipients, managers, schools);

    @Test
    void everyZoneManagerAndDirectorIsToldOnceAndOthersAreNot() {
        UUID director = UUID.randomUUID();
        UUID zoneManagerA = UUID.randomUUID();
        UUID zoneManagerB = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        when(recipients.activeDirectors()).thenReturn(Set.of(director, zoneManagerA));
        when(schools.school(any())).thenReturn(Optional.empty());
        when(managers.managerByUserId(any())).thenReturn(Optional.empty());

        listener.onEarned(new IncentiveEarned(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(zoneManagerA, zoneManagerB)));

        verify(notifications).create(eq(director), eq(NotificationType.INCENTIVE_PENDING), any());
        verify(notifications).create(eq(zoneManagerA), eq(NotificationType.INCENTIVE_PENDING), any());
        verify(notifications).create(eq(zoneManagerB), eq(NotificationType.INCENTIVE_PENDING), any());
        verify(notifications, never()).create(eq(bystander), any(), any());
    }
}
