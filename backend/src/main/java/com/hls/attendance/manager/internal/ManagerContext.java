package com.hls.attendance.manager.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Resolves the acting Manager from the signed-in user and reads the Manager's employment span (spec 032 FR-024).
 * A user with no Manager record is refused with 403 on the {@code /me} endpoints.
 */
@Component
public class ManagerContext {

    /** A Manager with the dates that bound their employment; either date may be null. */
    public record Acting(UUID managerId, UUID userId, String name, LocalDate joiningDate, LocalDate exitDate) {}

    private final ManagerQueries managers;

    public ManagerContext(ManagerQueries managers) {
        this.managers = managers;
    }

    /** The Manager linked to the user; 403 when the user has no Manager record. */
    public Acting resolve(UUID userId) {
        ManagerRef ref = managers.managerByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("The signed-in user is not linked to a Manager record."));
        return acting(ref);
    }

    /** A Manager by id, for the Admin and Director paths; 404 when unknown. */
    public Acting byManagerId(UUID managerId) {
        ManagerRef ref = managers.allManagers().stream()
                .filter(m -> m.id().equals(managerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Manager not found."));
        return acting(ref);
    }

    /**
     * Refuses unless the Manager has a joining date on or before {@code date} and no exit date before it. A Manager
     * with no joining date cannot check in or take leave until it is recorded (FR-024).
     */
    public void requireEmployedOn(Acting manager, LocalDate date) {
        if (manager.joiningDate() == null) {
            throw new InvalidInputException(
                    "Your joining date has not been recorded yet. Ask an Admin to record it first.");
        }
        if (date.isBefore(manager.joiningDate())) {
            throw new InvalidInputException("This date is before your joining date.");
        }
        if (manager.exitDate() != null && date.isAfter(manager.exitDate())) {
            throw new InvalidInputException("This date is after your exit date.");
        }
    }

    private Acting acting(ManagerRef ref) {
        ManagerEmployment employment = managers.employment(List.of(ref.id())).get(ref.id());
        return new Acting(
                ref.id(),
                ref.userId(),
                ref.displayName(),
                employment == null ? null : employment.joiningDate(),
                employment == null ? null : employment.exitDate());
    }
}
