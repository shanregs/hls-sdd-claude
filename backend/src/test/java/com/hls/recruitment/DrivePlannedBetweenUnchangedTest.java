package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.PlannedActivity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T015: plannedBetween of drives and the existing Change History entries are unchanged by the amendment. */
class DrivePlannedBetweenUnchangedTest extends RecruitmentTestBase {

    @Autowired
    DriveActivities feed;

    @Test
    void plannedBetweenReturnsOneEntryPerDateInEveryStatusAsBefore() {
        String admin = admin();
        UUID college = college(admin);
        UUID planned = drive(admin, college, List.of(), today.plusDays(2), today.plusDays(3));
        UUID held = drive(admin, college, List.of(), today.plusDays(2));
        post(BASE + "/drives/" + held + "/status", admin, Map.of("status", "HELD"));

        List<PlannedActivity> found = feed.plannedBetween(today, today.plusDays(10), null);

        assertThat(found.stream().filter(a -> a.id().equals(planned)).toList())
                .hasSize(2)
                .allSatisfy(a -> {
                    assertThat(a.kind()).isEqualTo("CAMPUS_DRIVE");
                    assertThat(a.status()).isEqualTo("PLANNED");
                    assertThat(a.ownerUserId()).isNotNull();
                    assertThat(a.place()).contains(", Chennai");
                });
        assertThat(found.stream().filter(a -> a.id().equals(held)).toList())
                .singleElement()
                .satisfies(a -> assertThat(a.status()).isEqualTo("HELD"));
    }

    @Test
    void changeHistoryStillRecordsTheDatesAndStatusChangesOfADrive() {
        String admin = admin();
        UUID college = college(admin);
        UUID drive = drive(admin, college, List.of(), today.plusDays(2));
        Map<String, Object> detail = get(BASE + "/drives/" + drive, admin).map();
        Map<String, Object> body = new java.util.HashMap<>(driveBody(college, List.of(), today.plusDays(5)));
        body.put("version", detail.get("version"));
        assertThat(put(BASE + "/drives/" + drive, admin, body).status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD")).status()).isEqualTo(200);

        assertChangeRecorded(admin, "CAMPUS_DRIVE", drive, "dates");
        assertChangeRecorded(admin, "CAMPUS_DRIVE", drive, "status");
    }
}
