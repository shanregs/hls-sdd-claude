package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import com.hls.teacher.api.TeacherDirectory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 008 research.md section 1: the read-only directory attendance uses for Teachers and placements. */
class TeacherDirectoryTest extends MasterDataTestBase {

    @Autowired
    private TeacherDirectory directory;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private final LocalDate today = LocalDate.now();

    @Test
    void placementsOverlappingARangeAreReturnedWithTheirSchools() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolInNewZone(admin)[2];
        UUID second = schoolInNewZone(admin)[2];
        placeTeacherRaw(admin, teacher, first, today.minusDays(20));
        placeTeacherRaw(admin, teacher, second, today.minusDays(10));

        List<TeacherDirectory.PlacementSpan> spans =
                directory.placementsOverlapping(Set.of(teacher), today.minusDays(15), today.minusDays(5));

        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).schoolId()).isEqualTo(first);
        assertThat(spans.get(0).endsOn()).isEqualTo(today.minusDays(11));
        assertThat(spans.get(1).schoolId()).isEqualTo(second);
        assertThat(spans.get(1).endsOn()).isNull();
        assertThat(spans.get(0).covers(today.minusDays(11))).isTrue();
        assertThat(spans.get(0).covers(today.minusDays(10))).isFalse();
    }

    @Test
    void anExitedTeacherIsNotPlacedOnOrAfterTheExitDay() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID school = schoolInNewZone(admin)[2];
        placeTeacherRaw(admin, teacher, school, today.minusDays(20));
        Resp exited = post(
                "/api/v1/teachers/" + teacher + "/status",
                admin,
                java.util.Map.of("status", "EXITED", "effectiveOn", today.minusDays(5).toString()));
        assertThat(exited.status()).as(exited.body()).isEqualTo(200);

        List<TeacherDirectory.PlacementSpan> spans =
                directory.placementsOverlapping(Set.of(teacher), today.minusDays(30), today);

        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).endsOn()).isEqualTo(today.minusDays(6));
    }

    @Test
    void teachersPlacedDuringIncludesPartMonthPlacementsAndExcludesUnplacedTeachers() {
        String admin = signInAs(Role.ADMIN).token();
        UUID placed = teacher(admin);
        UUID unplaced = teacher(admin);
        UUID school = schoolInNewZone(admin)[2];
        placeTeacherRaw(admin, placed, school, today.minusDays(3));

        Set<UUID> ids = directory.teachersPlacedDuring(today.minusDays(40), today);

        assertThat(ids).contains(placed).doesNotContain(unplaced);
        assertThat(directory.teachersPlacedDuring(today.minusDays(40), today.minusDays(10)))
                .doesNotContain(placed);
    }

    @Test
    void teacherInfoAndTeacherOfUserResolveTheLinkedRecord() {
        String admin = signInAs(Role.ADMIN).token();
        Signed user = signInAs(Role.TEACHER);
        UUID teacher = teacher(admin);
        put("/api/v1/teachers/" + teacher + "/user", admin, java.util.Map.of("userId", user.userId()));

        assertThat(directory.teacherOfUser(user.userId())).hasValueSatisfying(info -> {
            assertThat(info.id()).isEqualTo(teacher);
            assertThat(info.status()).isEqualTo("ACTIVE");
        });
        assertThat(directory.teacherInfo(Set.of(teacher))).containsKey(teacher);
        assertThat(directory.teacherOfUser(UUID.randomUUID())).isEmpty();
        assertThat(directory.teacherInfo(Set.of())).isEmpty();
    }

    /** Spec 014c A3: status, effective date and joining date (earliest placement start, 005 has no joining field). */
    @Test
    void lifecycleGivesStatusEffectiveDateAndJoiningDateInAFixedNumberOfQueries() {
        String admin = signInAs(Role.ADMIN).token();
        UUID joined = teacher(admin);
        UUID exited = teacher(admin);
        UUID neverPlaced = teacher(admin);
        UUID school = schoolInNewZone(admin)[2];
        UUID other = schoolInNewZone(admin)[2];
        placeTeacherRaw(admin, joined, school, today.minusDays(40));
        placeTeacherRaw(admin, joined, other, today.minusDays(10));
        placeTeacherRaw(admin, exited, school, today.minusDays(30));
        Resp resp = post(
                "/api/v1/teachers/" + exited + "/status", admin,
                java.util.Map.of("status", "EXITED", "effectiveOn", today.minusDays(5).toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        org.hibernate.stat.Statistics stats =
                entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        java.util.Map<UUID, TeacherDirectory.Lifecycle> result =
                directory.lifecycle(Set.of(joined, exited, neverPlaced, UUID.randomUUID()));

        assertThat(stats.getPrepareStatementCount()).as("fixed, not per Teacher").isLessThanOrEqualTo(6);
        assertThat(result).containsOnlyKeys(joined, exited, neverPlaced);
        assertThat(result.get(joined).status()).isEqualTo("ACTIVE");
        assertThat(result.get(joined).joinedOn()).isEqualTo(today.minusDays(40));
        assertThat(result.get(exited).status()).isEqualTo("EXITED");
        assertThat(result.get(exited).statusEffectiveOn()).isEqualTo(today.minusDays(5));
        assertThat(result.get(exited).joinedOn()).isEqualTo(today.minusDays(30));
        assertThat(result.get(neverPlaced).joinedOn()).isNull();
        assertThat(directory.lifecycle(Set.of())).isEmpty();
    }
}
