package com.hls.school.api;

import java.util.UUID;

/** Read access to the principal and accountant contacts of a School for other modules (billing, marketing). */
public interface SchoolContacts {

    SchoolContactsView contactsOf(UUID schoolId);

    /** Saves both contacts of a School on behalf of another module; a null contact clears that role. */
    SchoolContactsView replace(UUID actor, UUID schoolId, ContactDetails principal, ContactDetails accountant);
}
