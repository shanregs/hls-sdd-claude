package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import org.junit.jupiter.api.Test;

/** Spec 032 US1: the optional check-in photo. */
class CheckInPhotoTest extends ManagerAttendanceTestBase {

    private String photoUri() {
        return ME + "/days/" + today() + "/photo";
    }

    @Test
    void aJpgIsAttachedAndOpenedByTheManagerAnAdminAndADirectorOnly() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = joinedManager(admin);
        ManagerCtx other = joinedManager(admin);
        checkIn(manager.token(), "OFFICE");

        Resp attached = upload(photoUri(), manager.token(), "me.jpg", JPG);

        assertThat(attached.status()).as(attached.body()).isEqualTo(201);
        assertThat(attached.map()).containsEntry("hasPhoto", true);
        assertThat(get(photoUri(), manager.token()).status()).isEqualTo(200);
        String adminUri = ATT + "/managers/" + manager.managerId() + "/days/" + today() + "/photo";
        assertThat(get(adminUri, admin).status()).isEqualTo(200);
        assertThat(get(adminUri, director).status()).isEqualTo(200);
        assertThat(get(adminUri, other.token()).status()).isEqualTo(403);
        assertThat(get(adminUri, teacher().token()).status()).isEqualTo(403);
        // another Manager only ever sees their own day, which has no photo
        assertThat(get(photoUri(), other.token()).status()).isEqualTo(404);
    }

    @Test
    void aTextFileAnOversizeFileAndASecondPhotoAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        checkIn(manager.token(), "OFFICE");

        Resp text = upload(photoUri(), manager.token(), "notes.txt", "hello".getBytes());
        byte[] big = new byte[10 * 1024 * 1024 + 10];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        Resp oversize = upload(photoUri(), manager.token(), "big.png", big);
        Resp disguised = upload(photoUri(), manager.token(), "fake.png", "not an image".getBytes());
        Resp first = upload(photoUri(), manager.token(), "a.png", PNG);
        Resp second = upload(photoUri(), manager.token(), "b.png", PNG);

        assertThat(text.status()).isEqualTo(400);
        assertThat(oversize.status()).isEqualTo(400);
        assertThat(disguised.status()).isEqualTo(400);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(409);
    }

    @Test
    void theCheckInIsValidWithoutAPhoto() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        Resp resp = checkIn(manager.token(), "OFFICE");

        assertThat(resp.status()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("hasPhoto", false);
        assertThat(get(photoUri(), manager.token()).status()).isEqualTo(404);
    }

    @Test
    void aPhotoNeedsACheckIn() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        assertThat(upload(photoUri(), manager.token(), "a.png", PNG).status()).isEqualTo(404);
    }
}
