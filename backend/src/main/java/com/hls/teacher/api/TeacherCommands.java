package com.hls.teacher.api;

import com.hls.teacher.api.dto.CreateTeacherProfileRequest;
import com.hls.teacher.api.dto.TeacherProfileView;
import com.hls.teacher.api.dto.TeacherStatus;
import com.hls.teacher.api.dto.UpdateTeacherProfileRequest;
import java.util.UUID;

/**
 * Public write surface for Teacher Profiles (FR-001/002/003). Every method
 * here is Admin-only — enforced by {@code TeacherController} reading the
 * caller's role from the JWT directly, not by this interface.
 */
public interface TeacherCommands {

    TeacherProfileView create(CreateTeacherProfileRequest request, UUID actingUserId);

    TeacherProfileView updateProfile(UUID teacherId, UpdateTeacherProfileRequest request, UUID actingUserId);

    TeacherProfileView changeStatus(UUID teacherId, TeacherStatus newStatus, UUID actingUserId);
}
