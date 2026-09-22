package com.hls.teacher.api.dto;

/**
 * FR-002. Every field optional — only those present are changed. No salary
 * field — a salary change is its own operation
 * (specs/009-teacher-salary-history's {@code TeacherSalaryCommands.recordSalaryChange}),
 * since it carries an effective date a contact-detail change doesn't need.
 */
public record UpdateTeacherProfileRequest(String name, String phone, String email) {
}
