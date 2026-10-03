package com.hls.teacher.web;

import com.hls.school.api.CallerContext;
import com.hls.teacher.api.TeacherView;
import com.hls.teacher.internal.TeacherService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A Teacher reads only their own profile (spec 005 US7); no salary, no other Teacher. */
@RestController
public class TeacherMeController {

    private final TeacherService teacherService;

    public TeacherMeController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/api/v1/teachers/me")
    public TeacherView mine(@AuthenticationPrincipal Jwt jwt) {
        return teacherService.mine(CallerContext.userId(jwt));
    }
}
