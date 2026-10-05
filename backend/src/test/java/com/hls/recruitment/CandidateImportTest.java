package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateImportTest extends RecruitmentTestBase {

    private static final String HEADER = "name,phone,email,degree,year,notes\n";

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errors(Resp resp) {
        return (List<Map<String, Object>>) resp.map().get("errors");
    }

    @Test
    void tenRowsWithOneInvalidSaveNineAndReportTheRowAndReason() {
        String admin = admin();
        UUID drive = drive(admin);
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 1; i <= 10; i++) {
            String phone = i == 4 ? "12345" : phone();
            csv.append("Student ").append(i).append(',').append(phone).append(",s").append(i).append("@mail.test,B.Sc,Final,\n");
        }

        Resp resp = upload(admin, drive, csv.toString());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("saved", 9).containsEntry("skipped", 0);
        assertThat(errors(resp)).hasSize(1);
        assertThat(errors(resp).get(0)).containsEntry("row", 5);
        assertThat((String) errors(resp).get(0).get("reason")).contains("10 digits");
    }

    @Test
    void theSameFileAgainSavesNoneBecauseThePhoneIsAlreadyInTheDrive() {
        String admin = admin();
        UUID drive = drive(admin);
        String csv = HEADER + "Asha,+91 " + phone() + ",,,,\nRavi," + phone() + ",,,,\n";

        assertThat(upload(admin, drive, csv).map()).containsEntry("saved", 2);
        Resp again = upload(admin, drive, csv);

        assertThat(again.map()).containsEntry("saved", 0).containsEntry("skipped", 2);
    }

    @Test
    void phoneNumbersAreNormalizedSoAFormattedDuplicateIsCaught() {
        String admin = admin();
        UUID drive = drive(admin);
        String digits = phone();
        String formatted = "+91 " + digits.substring(0, 5) + "-" + digits.substring(5);

        assertThat(addCandidate(admin, drive, "First", digits).status()).isEqualTo(201);

        assertThat(addCandidate(admin, drive, "Second", formatted).status()).isEqualTo(409);
    }

    @Test
    void aFiveHundredRowFileImportsInChunks() {
        String admin = admin();
        UUID drive = drive(admin);
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 500; i++) {
            csv.append("Bulk ").append(i).append(',').append(phone()).append(",,,,\n");
        }

        Resp resp = upload(admin, drive, csv.toString());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("saved", 500);
        assertThat(get(BASE + "/candidates?drive=" + drive, admin).body()).contains("Bulk 499");
    }

    @Test
    void aMalformedHeaderOrAnEmptyFileIsA400() {
        String admin = admin();
        UUID drive = drive(admin);

        assertThat(upload(admin, drive, "fullname,mobile\nA,1234567890\n").status()).isEqualTo(400);
        assertThat(upload(admin, drive, "name,email\nA,a@b.test\n").status()).isEqualTo(400);
        assertThat(upload(admin, drive, "").status()).isEqualTo(400);
    }

    @Test
    void quotedCellsWithCommasAreKeptTogether() {
        String admin = admin();
        UUID drive = drive(admin);
        String csv = HEADER + "\"Kumar, R\"," + phone() + ",,\"B.E, CSE\",Final,\"likes \"\"quotes\"\"\"\n";

        Resp resp = upload(admin, drive, csv);

        assertThat(resp.map()).containsEntry("saved", 1);
        String body = get(BASE + "/candidates?drive=" + drive, admin).body();
        assertThat(body).contains("Kumar, R").contains("B.E, CSE");
    }

    @Test
    void aZoneManagerCannotImportIntoSomeoneElsesDrive() {
        String admin = admin();
        UUID drive = drive(admin);
        String manager = signInAs(com.hls.identity.user.Role.MANAGER).token();

        assertThat(upload(manager, drive, HEADER + "A," + phone() + ",,,,\n").status()).isEqualTo(403);
        assertThat(upload(null, drive, HEADER).status()).isEqualTo(401);
    }
}
