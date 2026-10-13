package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.attendance.api.ManagerMonth.Found;
import com.hls.attendance.api.UnpaidDay;
import com.hls.identity.user.Role;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Spec 032 US7: what payroll reads of a Manager's month, against figures worked out by hand. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ManagerAttendanceReadApiTest extends ManagerAttendanceTestBase {

    @Autowired
    private ManagerAttendanceReadApi readApi;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Leave on a date, creating the row when there is none. */
    private void leave(UUID managerId, LocalDate date, String fraction, boolean unpaid) {
        int updated = jdbc.update(
                "update manager_day set leave_request_id = ?, leave_fraction = ?, leave_unpaid = ? where manager_id = ? and day_date = ?",
                UUID.randomUUID(),
                bd(fraction),
                unpaid,
                managerId,
                date);
        if (updated == 0) {
            jdbc.update(
                    "insert into manager_day (id, manager_id, day_date, leave_request_id, leave_fraction, leave_unpaid, version)"
                            + " values (?, ?, ?, ?, ?, ?, 0)",
                    UUID.randomUUID(),
                    managerId,
                    date,
                    UUID.randomUUID(),
                    bd(fraction),
                    unpaid);
        }
    }

    private void worked(UUID managerId, LocalDate date, boolean checkedOut) {
        seedDay(managerId, date, instant(date, 9, 0), checkedOut ? instant(date, 18, 0) : null);
    }

    @Test
    void theMonthMatchesTheHandComputedFigures() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        List<LocalDate> w = workingDates(lastMonth());
        // employed on exactly the first eight working days of last month
        setJoining(manager.managerId(), w.get(0), w.get(7));
        worked(manager.managerId(), w.get(0), true); // worked
        worked(manager.managerId(), w.get(1), false); // no check-out still counts as worked
        seedFieldDay(manager.managerId(), w.get(2), "School visit", instant(w.get(2), 9, 0), instant(w.get(2), 17, 0)); // field
        leave(manager.managerId(), w.get(3), "1.0", false); // paid leave
        leave(manager.managerId(), w.get(4), "1.0", true); // Loss-of-Pay leave
        worked(manager.managerId(), w.get(5), true);
        leave(manager.managerId(), w.get(5), "0.5", false); // half day paid leave, half day worked
        leave(manager.managerId(), w.get(6), "0.5", true); // half day Loss-of-Pay leave, no check-in
        // w.get(7): nothing at all
        // a check-in on a day that is not a working day changes no figure
        LocalDate off = off(w.get(0), w.get(7));
        if (off != null) {
            worked(manager.managerId(), off, true);
        }

        Found month = (Found) readApi.month(manager.managerId(), lastMonth());

        assertThat(month.workingDays()).isEqualByComparingTo("8");
        assertThat(month.daysWorked()).isEqualByComparingTo("3.5");
        assertThat(month.fieldDays()).isEqualByComparingTo("1");
        assertThat(month.leavePaid()).isEqualByComparingTo("1.5");
        assertThat(month.leaveUnpaid()).isEqualByComparingTo("1.5");
        assertThat(month.unpaid()).isEqualByComparingTo("3");
        assertThat(month.unpaidDays())
                .extracting(UnpaidDay::date)
                .containsExactly(w.get(4), w.get(6), w.get(7));
        assertThat(month.unpaidDays()).allSatisfy(d -> assertThat(d.fraction()).isEqualByComparingTo("1"));
        assertThat(month.pending()).isEmpty();
        assertThat(month.locked()).isFalse();
    }

    @Test
    void aHalfDayWithNoCheckInIsHalfUnpaidWhenTheLeaveIsPaid() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        List<LocalDate> w = workingDates(lastMonth());
        setJoining(manager.managerId(), w.get(0), w.get(1));
        leave(manager.managerId(), w.get(0), "0.5", false);
        worked(manager.managerId(), w.get(1), true);

        Found month = (Found) readApi.month(manager.managerId(), lastMonth());

        assertThat(month.workingDays()).isEqualByComparingTo("2");
        assertThat(month.leavePaid()).isEqualByComparingTo("0.5");
        assertThat(month.daysWorked()).isEqualByComparingTo("1");
        assertThat(month.unpaid()).isEqualByComparingTo("0.5");
        assertThat(month.unpaidDays()).hasSize(1);
        assertThat(month.unpaidDays().get(0).date()).isEqualTo(w.get(0));
        assertThat(month.unpaidDays().get(0).fraction()).isEqualByComparingTo("0.5");
    }

    @Test
    void aMidMonthJoinerIsCountedFromTheirJoiningDate() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        List<LocalDate> w = workingDates(lastMonth());
        setJoining(manager.managerId(), w.get(3), null);

        Found month = (Found) readApi.month(manager.managerId(), lastMonth());

        assertThat(month.workingDays()).isEqualByComparingTo(String.valueOf(w.size() - 3));
        assertThat(month.unpaid()).isEqualByComparingTo(String.valueOf(w.size() - 3));
        assertThat(month.daysWorked()).isEqualByComparingTo("0");
    }

    @Test
    void aHolidayAddedToTheCalendarIsNotAWorkingDay() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        YearMonth month = YearMonth.from(today()).minusMonths(2);
        List<LocalDate> before = workingDates(month);
        setJoining(manager.managerId(), before.get(0), null);
        LocalDate holiday = before.get(4);
        addHoliday(admin, holiday);
        try {
            Found found = (Found) readApi.month(manager.managerId(), month);

            assertThat(found.workingDays()).isEqualByComparingTo(String.valueOf(before.size() - 1));
            assertThat(found.unpaidDays()).extracting(UnpaidDay::date).doesNotContain(holiday);
        } finally {
            removeHoliday(admin, holiday);
        }
    }

    @Test
    void todayAndLaterArePendingNotUnpaid() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        YearMonth thisMonth = YearMonth.from(today());
        setJoining(manager.managerId(), thisMonth.atDay(1).minusMonths(1), null);

        Found month = (Found) readApi.month(manager.managerId(), thisMonth);

        assertThat(month.unpaidDays()).extracting(UnpaidDay::date).allMatch(d -> d.isBefore(today()));
        assertThat(month.pending()).allMatch(d -> !d.isBefore(today()));
        assertThat(month.unpaid().add(BigDecimal.valueOf(month.pending().size())).add(month.daysWorked()))
                .isEqualByComparingTo(month.workingDays());
    }

    @Test
    void aManagerWithNoJoiningDateOrNoRecordSaysSoInsteadOfZero() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx none = newManager(admin);

        assertThat(readApi.month(none.managerId(), lastMonth())).isInstanceOf(ManagerMonth.NoJoiningDate.class);
        assertThat(readApi.month(UUID.randomUUID(), lastMonth())).isInstanceOf(ManagerMonth.Unknown.class);
        Map<UUID, ManagerMonth> bulk = readApi.months(List.of(none.managerId()), lastMonth());
        assertThat(bulk).containsOnlyKeys(none.managerId());
    }

    @Test
    void aLockedMonthReturnsTheFrozenFiguresEvenIfTheRecordsChange() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        List<LocalDate> w = workingDates(lastMonth());
        lockMonth(manager.managerId(), lastMonth());
        jdbc.update(
                "update manager_month set frozen_working_days = 20, frozen_days_worked = 17, frozen_leave_paid = 1,"
                        + " frozen_leave_unpaid = 0.5, frozen_unpaid = 2, frozen_field_days = 3, frozen_unpaid_dates = ?"
                        + " where manager_id = ?",
                w.get(1) + ":1.0," + w.get(2) + ":1.0",
                manager.managerId());
        worked(manager.managerId(), w.get(1), true);

        Found locked = (Found) readApi.month(manager.managerId(), lastMonth());

        assertThat(locked.locked()).isTrue();
        assertThat(locked.workingDays()).isEqualByComparingTo("20");
        assertThat(locked.daysWorked()).isEqualByComparingTo("17");
        assertThat(locked.unpaid()).isEqualByComparingTo("2");
        assertThat(locked.fieldDays()).isEqualByComparingTo("3");
        assertThat(locked.unpaidDays()).extracting(UnpaidDay::date).containsExactly(w.get(1), w.get(2));
        assertThat(locked.pending()).isEmpty();

        jdbc.update("delete from manager_month where manager_id = ?", manager.managerId());
        Found open = (Found) readApi.month(manager.managerId(), lastMonth());
        assertThat(open.locked()).isFalse();
        assertThat(open.daysWorked()).isEqualByComparingTo("1");
    }

    @Test
    void nothingReturnedCarriesALocationOrAPhoto() {
        for (var component : Found.class.getRecordComponents()) {
            assertThat(component.getName().toLowerCase()).doesNotContain("lat", "lng", "photo", "place", "location");
        }
        assertThat(UnpaidDay.class.getRecordComponents()).hasSize(2);
    }

    @Test
    void theBulkFormEqualsTheSingleFormForFortyManagersInAFixedNumberOfQueries() {
        String admin = signInAs(Role.ADMIN).token();
        List<LocalDate> w = workingDates(lastMonth());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ManagerCtx m = longJoinedManager(admin);
            worked(m.managerId(), w.get(i % 5), true);
            if (i % 3 == 0) {
                leave(m.managerId(), w.get(6), "1.0", i % 2 == 0);
            }
            ids.add(m.managerId());
        }
        ids.add(UUID.randomUUID());
        ids.add(newManager(admin).managerId());
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long lowest = Long.MAX_VALUE;
        Map<UUID, ManagerMonth> bulk = null;
        for (int run = 0; run < 3; run++) {
            pause();
            stats.clear();
            bulk = readApi.months(ids, lastMonth());
            lowest = Math.min(lowest, stats.getPrepareStatementCount());
        }

        assertThat(lowest).as("statements for 42 asked").isLessThanOrEqualTo(12);
        assertThat(bulk).containsOnlyKeys(ids);
        for (UUID id : ids) {
            assertThat(readApi.month(id, lastMonth())).as(id.toString()).isEqualTo(bulk.get(id));
        }
    }

    /** A non-working date strictly between two working dates, or null. */
    private LocalDate off(LocalDate first, LocalDate last) {
        List<LocalDate> working = workingDates(lastMonth());
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            if (!working.contains(d)) {
                return d;
            }
        }
        return null;
    }

    private static void pause() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
