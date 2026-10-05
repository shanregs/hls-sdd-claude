package com.hls.school.api;

import java.util.UUID;

/** Read access to the principal and accountant contacts of a School for other modules (billing, marketing). */
public interface SchoolContacts {

    SchoolContactsView contactsOf(UUID schoolId);
}
