package com.hls.teacher.internal;

import java.util.UUID;

/** Thrown by {@link TeacherService} when a teacherId names no profile; mapped to 404 by {@link TeacherController}. */
class TeacherNotFoundException extends RuntimeException {

    TeacherNotFoundException(UUID teacherId) {
        super("No teacher profile with id " + teacherId);
    }
}
