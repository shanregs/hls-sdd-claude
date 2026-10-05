package com.hls.school.api;

/** The principal and accountant contacts of a School (amendment A8 to spec 005); a missing one is null. */
public record SchoolContactsView(ContactDetails principal, ContactDetails accountant) {}
