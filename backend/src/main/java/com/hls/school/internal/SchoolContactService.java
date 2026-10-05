package com.hls.school.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ContactDetails;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolContacts;
import com.hls.school.api.SchoolContactsView;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Principal and accountant contacts of a School (amendment A8 to spec 005). Visibility and scope follow the School
 * itself: a caller who cannot see the School gets "not found".
 */
@Service
public class SchoolContactService implements SchoolContacts {

    static final String PRINCIPAL = "PRINCIPAL";
    static final String ACCOUNTANT = "ACCOUNTANT";

    private final SchoolContactRepository repository;
    private final SchoolService schoolService;
    private final ChangeRecorder changes;

    public SchoolContactService(SchoolContactRepository repository, SchoolService schoolService, ChangeRecorder changes) {
        this.repository = repository;
        this.schoolService = schoolService;
        this.changes = changes;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolContactsView contactsOf(UUID schoolId) {
        return viewOf(repository.findBySchoolId(schoolId));
    }

    @Transactional(readOnly = true)
    public SchoolContactsView get(UUID userId, Set<Role> roles, UUID schoolId) {
        schoolService.get(userId, roles, schoolId);
        return contactsOf(schoolId);
    }

    /** Replaces both contacts; a null contact clears that role. */
    @Transactional
    public SchoolContactsView replace(
            UUID actor, Set<Role> roles, UUID schoolId, ContactDetails principal, ContactDetails accountant) {
        schoolService.get(actor, roles, schoolId);
        apply(actor, schoolId, PRINCIPAL, clean(principal, "Principal"));
        apply(actor, schoolId, ACCOUNTANT, clean(accountant, "Accountant"));
        return contactsOf(schoolId);
    }

    private void apply(UUID actor, UUID schoolId, String role, ContactDetails wanted) {
        SchoolContact existing = repository.findById(new SchoolContact.Key(schoolId, role)).orElse(null);
        ContactDetails before = existing == null ? null : toDetails(existing);
        if (Objects.equals(before, wanted)) {
            return;
        }
        if (wanted == null) {
            repository.delete(existing);
        } else if (existing == null) {
            repository.save(new SchoolContact(schoolId, role, wanted.name(), wanted.phone(), wanted.email()));
        } else {
            existing.change(wanted.name(), wanted.phone(), wanted.email());
            repository.save(existing);
        }
        repository.flush();
        changes.record(actor, "SCHOOL", schoolId, role.toLowerCase(), describe(before), describe(wanted));
    }

    private static ContactDetails clean(ContactDetails in, String label) {
        if (in == null) {
            return null;
        }
        String name = blankToNull(in.name());
        String phone = blankToNull(in.phone());
        String email = blankToNull(in.email());
        if (name == null && phone == null && email == null) {
            return null;
        }
        if (name == null) {
            throw new InvalidInputException(label + " contact needs a name.");
        }
        return new ContactDetails(
                max(name, 160, label + " name"), max(phone, 20, label + " phone"), max(email, 200, label + " email"));
    }

    private static String max(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static SchoolContactsView viewOf(List<SchoolContact> rows) {
        ContactDetails principal = null;
        ContactDetails accountant = null;
        for (SchoolContact row : rows) {
            if (PRINCIPAL.equals(row.getRole())) {
                principal = toDetails(row);
            } else if (ACCOUNTANT.equals(row.getRole())) {
                accountant = toDetails(row);
            }
        }
        return new SchoolContactsView(principal, accountant);
    }

    private static ContactDetails toDetails(SchoolContact row) {
        return new ContactDetails(row.getName(), row.getPhone(), row.getEmail());
    }

    /** The audit text carries the name only: phone and email are not written to the change history. */
    private static String describe(ContactDetails details) {
        return details == null ? null : details.name();
    }
}
