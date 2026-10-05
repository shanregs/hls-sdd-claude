package com.hls.school.api;

/** One named contact person of a School (principal or accountant): the name is required, phone and email are optional. */
public record ContactDetails(String name, String phone, String email) {}
