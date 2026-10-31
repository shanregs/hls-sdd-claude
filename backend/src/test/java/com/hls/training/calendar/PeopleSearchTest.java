package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 024: the person picker for individual assignment and walk-ins keeps to the caller's scope. */
class PeopleSearchTest extends TrainingCalendarTestBase {

    @Test
    void adminFindsTeachersAndManagersAndCanNarrowByKind() {
        World w = newWorld();
        List<Map<String, Object>> all = list(get(API + "/people", w.admin()));
        assertThat(all).extracting(r -> r.get("id")).contains(
                w.teacherA().teacherId().toString(), w.teacherB().teacherId().toString(),
                w.managerA().managerId().toString(), w.managerB().managerId().toString());
        List<Map<String, Object>> managers = list(get(API + "/people?kind=MANAGER", w.admin()));
        assertThat(managers).extracting(r -> r.get("kind")).containsOnly("MANAGER");
        assertThat(all.size()).isLessThanOrEqualTo(20);
    }

    @Test
    void aManagerFindsOnlyTheirOwnTeachers() {
        World w = newWorld();
        List<Map<String, Object>> found = list(get(API + "/people", w.managerA().token()));
        assertThat(found).extracting(r -> r.get("id")).contains(w.teacherA().teacherId().toString());
        assertThat(found).extracting(r -> r.get("id")).doesNotContain(
                w.teacherB().teacherId().toString(), w.managerA().managerId().toString(), w.managerB().managerId().toString());
        assertThat(found).extracting(r -> r.get("kind")).containsOnly("TEACHER");
    }

    @Test
    void aTeacherCannotSearch() {
        World w = newWorld();
        assertThat(get(API + "/people", w.teacherA().token()).status()).isEqualTo(403);
    }
}
