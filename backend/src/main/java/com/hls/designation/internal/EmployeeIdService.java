package com.hls.designation.internal;

import com.hls.designation.api.EmployeeIds;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee-id registry over {@code employee_id_claim}. The primary key (trimmed, lower-cased id) is the uniqueness
 * guarantee, so two simultaneous requests for one id cannot both succeed.
 */
@Service
class EmployeeIdService implements EmployeeIds {

    static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{1,20}");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    EmployeeIdService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String claim(PersonKind kind, UUID personId, String personName, String employeeId) {
        String id = employeeId == null ? "" : employeeId.trim();
        if (id.isEmpty()) {
            jdbc.update("delete from employee_id_claim where person_id = ?", personId);
            return null;
        }
        if (!FORMAT.matcher(id).matches()) {
            throw new InvalidInputException("The employee id must be 1 to 20 letters, digits or hyphens.");
        }
        String key = id.toLowerCase(Locale.ROOT);
        String name = personName == null || personName.isBlank() ? "another person" : personName;
        List<String> own = jdbc.queryForList(
                "select employee_key from employee_id_claim where person_id = ?", String.class, personId);
        if (!own.isEmpty()) {
            if (own.get(0).equals(key)) {
                jdbc.update(
                        "update employee_id_claim set employee_id = ?, person_name = ? where person_id = ?",
                        id,
                        name,
                        personId);
                return id;
            }
            jdbc.update("delete from employee_id_claim where person_id = ?", personId);
        }
        int inserted = jdbc.update(
                """
                insert into employee_id_claim (employee_key, person_kind, person_id, employee_id, person_name, claimed_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (employee_key) do nothing
                """,
                key,
                kind.name(),
                personId,
                id,
                name,
                Timestamp.from(clock.instant()));
        if (inserted == 0) {
            String holder = jdbc.queryForList(
                            "select person_name from employee_id_claim where employee_key = ?", String.class, key)
                    .stream()
                    .findFirst()
                    .orElse("another person");
            throw new ConflictException("The employee id " + id + " is already used by " + holder + ".");
        }
        return id;
    }
}
