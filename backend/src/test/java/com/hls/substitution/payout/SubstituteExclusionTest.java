package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.SubstitutionTestBase;
import com.hls.teacher.api.TeacherDirectory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 017b T013 (amendment to 008): a Substitute is never one of the Regular Teachers the grid and tiles count. */
class SubstituteExclusionTest extends SubstitutionTestBase {

    @Autowired
    private TeacherDirectory teachers;

    @Test
    void aSubstituteIsInTheSubstitutePoolAndNotInTheRegularLists() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);

        List<UUID> regular = teachers.activeTeachers().stream().map(TeacherDirectory.TeacherInfo::id).toList();
        List<UUID> pool = teachers.activeSubstitutes().stream().map(TeacherDirectory.TeacherInfo::id).toList();

        assertThat(regular).doesNotContain(substitute.teacherId()).contains(w.teacherA().teacherId());
        assertThat(pool).contains(substitute.teacherId()).doesNotContain(w.teacherA().teacherId());
        assertThat(teachers.substituteTeacherIds()).contains(substitute.teacherId());
        assertThat(teachers.teachersPlacedDuring(today().minusDays(30), today())).doesNotContain(substitute.teacherId());
    }

    @Test
    void anAttendanceMarkOnASubstituteIsRefusedBecauseTheyHaveNoPlacement() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);

        Resp resp = markAbsent(w.managerA().token(), substitute.teacherId(), monday(), 1);

        assertThat(resp.status()).isIn(404, 409, 400);
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ?", Integer.class, substitute.teacherId()))
                .isZero();
    }
}
