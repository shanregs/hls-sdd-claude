package com.hls.payroll.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.leave.api.LeaveTypes;
import com.hls.payroll.api.LossOfPayValue;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayableDays;
import com.hls.payroll.api.SalaryInEffect;
import com.hls.payroll.api.SalaryInEffect.Reason;
import com.hls.payroll.api.SalaryInEffect.Source;
import com.hls.payroll.api.Span;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.payroll.api.TotalLossOfPay.UnpaidDayValue;
import com.hls.payroll.api.UnpaidDay;
import com.hls.payroll.api.UnpaidDays;
import com.hls.payroll.internal.LossOfPayCalculator.DayInput;
import com.hls.payroll.internal.PayableDaysCalculator.Employment;
import com.hls.payroll.internal.PersonDirectory.ManagerDates;
import com.hls.payroll.internal.PersonDirectory.Placement;
import com.hls.payroll.internal.PersonDirectory.StaffUser;
import com.hls.payroll.internal.PersonDirectory.RecordedSalary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one public interface payroll asks (spec 013a, contracts/pay-rules-interface.md). It only reads: no write path,
 * no clock (dates are passed in, "today" is never consulted), and every answer comes from the pure calculators over
 * stored rows, so the same inputs always give the same answer. The single-person forms are the bulk forms asked about
 * one person, so the two can never disagree.
 */
@Service
@Transactional(readOnly = true)
class PayRulesImpl implements PayRules {

    static final String MANAGER_ATTENDANCE_NOT_RECORDED = "Manager attendance is not recorded yet";
    static final String STAFF_ATTENDANCE_NOT_RECORDED = "Staff attendance is not recorded yet";
    static final String UNKNOWN_TEACHER = "The Teacher is not known.";

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    /** One person's month: the payable dates, and which School's calendar each date and the month use. */
    private record Computed(
            boolean known,
            PayableDays days,
            Map<LocalDate, UUID> schoolByDate,
            Map<UUID, Integer> monthWorkingDaysBySchool) {}

    private final PersonSalaries personSalaries;
    private final StaffEmploymentRepository staffDates;
    private final PayPolicyService policies;
    private final PersonDirectory people;
    private final AttendanceReadApi attendance;
    private final WorkingCalendar calendar;
    private final LeaveTypes leaveTypes;

    PayRulesImpl(
            PersonSalaries personSalaries,
            StaffEmploymentRepository staffDates,
            PayPolicyService policies,
            PersonDirectory people,
            AttendanceReadApi attendance,
            WorkingCalendar calendar,
            LeaveTypes leaveTypes) {
        this.personSalaries = personSalaries;
        this.staffDates = staffDates;
        this.policies = policies;
        this.people = people;
        this.attendance = attendance;
        this.calendar = calendar;
        this.leaveTypes = leaveTypes;
    }

    // --------------------------------------------------------------- salary

    @Override
    public SalaryInEffect salaryOn(PersonRef person, LocalDate date) {
        return salariesOn(List.of(person), date).get(person);
    }

    @Override
    public Map<PersonRef, SalaryInEffect> salariesOn(Collection<PersonRef> asked, LocalDate date) {
        Set<PersonRef> persons = new LinkedHashSet<>(asked);
        Set<UUID> teacherIds = idsOf(persons, Kind.TEACHER);
        Set<UUID> managerIds = idsOf(persons, Kind.MANAGER);
        Set<UUID> staffIds = idsOf(persons, Kind.STAFF);

        Set<UUID> knownTeachers = people.existingTeachers(teacherIds);
        Map<UUID, RecordedSalary> ownSalary = people.teacherSalariesOn(knownTeachers, date);

        // a Manager's or staff member's salary is their own history, whatever their designation (FR-005, amended)
        Map<UUID, ManagerDates> managers = people.managers(managerIds);
        Set<UUID> employedManagers = managers.entrySet().stream()
                .filter(e -> employedOn(e.getValue().joiningDate(), e.getValue().exitDate(), date))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<UUID, PersonSalary> managerSalary = personSalaries.inEffect(PersonSalary.Kind.MANAGER, employedManagers, date);

        Map<UUID, StaffUser> staff = people.staff(staffIds);
        Map<UUID, StaffEmployment> staffDatesById = staffDatesOf(staff.keySet());
        Set<UUID> employedStaff = staff.keySet().stream()
                .filter(id -> {
                    StaffEmployment dates = staffDatesById.get(id);
                    return dates == null || employedOn(dates.getJoiningDate(), dates.getExitDate(), date);
                })
                .collect(Collectors.toSet());
        Map<UUID, PersonSalary> staffSalary = personSalaries.inEffect(PersonSalary.Kind.STAFF, employedStaff, date);

        Map<PersonRef, SalaryInEffect> result = new LinkedHashMap<>();
        for (PersonRef person : persons) {
            result.put(person, switch (person.kind()) {
                case TEACHER -> teacherSalary(person.id(), knownTeachers, ownSalary);
                case MANAGER -> personSalary(person.id(), employedManagers, managerSalary);
                case STAFF -> personSalary(person.id(), employedStaff, staffSalary);
            });
        }
        return result;
    }

    private Map<UUID, StaffEmployment> staffDatesOf(Collection<UUID> userIds) {
        return userIds.isEmpty()
                ? Map.of()
                : staffDates.findAllById(userIds).stream()
                        .collect(Collectors.toMap(StaffEmployment::getUserId, d -> d));
    }

    private static SalaryInEffect personSalary(UUID id, Set<UUID> employed, Map<UUID, PersonSalary> salaryOf) {
        if (!employed.contains(id)) {
            return new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED);
        }
        PersonSalary row = salaryOf.get(id);
        return row == null
                ? new SalaryInEffect.NoSalary(Reason.PERSON_HAS_NO_RECORDED_SALARY)
                : new SalaryInEffect.Found(row.getAmount(), row.effectiveOn(), Source.PERSON_RECORD);
    }

    private static SalaryInEffect teacherSalary(
            UUID id, Set<UUID> known, Map<UUID, RecordedSalary> ownSalary) {
        if (!known.contains(id)) {
            return new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED);
        }
        // the designation's salary is never a fallback for a Teacher (FR-005, clarification 3)
        RecordedSalary own = ownSalary.get(id);
        return own == null
                ? new SalaryInEffect.NoSalary(Reason.TEACHER_HAS_NO_RECORDED_SALARY)
                : new SalaryInEffect.Found(own.amount(), own.effectiveOn(), Source.TEACHER_RECORD);
    }

    private static boolean employedOn(LocalDate joining, LocalDate exit, LocalDate date) {
        return (joining == null || !date.isBefore(joining)) && (exit == null || !date.isAfter(exit));
    }

    // ---------------------------------------------------------- loss of pay

    @Override
    public LossOfPayValue lossOfPayValueOn(PersonRef person, LocalDate date) {
        SalaryInEffect salary = salaryOn(person, date);
        if (salary instanceof SalaryInEffect.NoSalary none) {
            return new LossOfPayValue.NoSalary(none);
        }
        BigDecimal monthly = ((SalaryInEffect.Found) salary).monthly();
        YearMonth month = YearMonth.from(date);
        int workingDays = calendar.workingDays(schoolOn(person, date), month.atDay(1), month.atEndOfMonth()).size();
        if (workingDays == 0) {
            return new LossOfPayValue.NoWorkingDays(month);
        }
        BigDecimal full = LossOfPayCalculator.fullDay(monthly, workingDays);
        BigDecimal half = LossOfPayCalculator.halfDay(full, policyOfMonth(month).halfDayFraction());
        return new LossOfPayValue.Found(full, half, workingDays);
    }

    /** The School whose calendar a person's day follows: a Teacher's placement on the date, otherwise the default. */
    private UUID schoolOn(PersonRef person, LocalDate date) {
        if (person.kind() != Kind.TEACHER) {
            return null;
        }
        return people.placements(List.of(person.id()), date, date).stream()
                .filter(p -> !p.from().isAfter(date) && (p.to() == null || !p.to().isBefore(date)))
                .map(Placement::schoolId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public PayPolicyView policyOn(LocalDate date) {
        return policies.policyOn(date);
    }

    /** One policy governs a whole month's total: the version in effect on the month's last day. */
    private PayPolicyView policyOfMonth(YearMonth month) {
        return policies.policyOn(month.atEndOfMonth());
    }

    // ---------------------------------------------------------- payable days

    @Override
    public PayableDays payableDays(PersonRef person, YearMonth month) {
        return payableDays(List.of(person), month).get(person);
    }

    @Override
    public Map<PersonRef, PayableDays> payableDays(Collection<PersonRef> asked, YearMonth month) {
        Map<PersonRef, PayableDays> result = new LinkedHashMap<>();
        compute(asked, month).forEach((person, computed) -> result.put(person, computed.days()));
        return result;
    }

    private Map<PersonRef, Computed> compute(Collection<PersonRef> asked, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Set<PersonRef> persons = new LinkedHashSet<>(asked);
        Set<UUID> teacherIds = idsOf(persons, Kind.TEACHER);
        Set<UUID> managerIds = idsOf(persons, Kind.MANAGER);
        Set<UUID> staffIds = idsOf(persons, Kind.STAFF);

        Set<UUID> knownTeachers = people.existingTeachers(teacherIds);
        Map<UUID, List<Placement>> placements =
                people.placements(knownTeachers, first, last).stream().collect(Collectors.groupingBy(Placement::teacherId));
        Map<UUID, ManagerDates> managers = people.managers(managerIds);
        Map<UUID, StaffUser> staff = people.staff(staffIds);
        Map<UUID, StaffEmployment> staffDatesById = staffDatesOf(staff.keySet());

        // one calendar read for every School any person was placed in, and the default calendar (null key)
        Set<UUID> schools = new HashSet<>();
        schools.add(null);
        placements.values().forEach(list -> list.forEach(p -> schools.add(p.schoolId())));
        Map<UUID, List<LocalDate>> workingDays = calendar.workingDaysBySchool(schools, first, last);
        Map<UUID, Integer> monthWorkingDays = new HashMap<>();
        workingDays.forEach((school, days) -> monthWorkingDays.put(school, days.size()));

        Map<PersonRef, Computed> result = new LinkedHashMap<>();
        for (PersonRef person : persons) {
            List<Employment> employment;
            boolean known;
            if (person.kind() == Kind.TEACHER) {
                known = knownTeachers.contains(person.id());
                employment = placements.getOrDefault(person.id(), List.of()).stream()
                        .map(p -> new Employment(p.schoolId(), p.from(), p.to()))
                        .toList();
            } else if (person.kind() == Kind.MANAGER) {
                ManagerDates dates = managers.get(person.id());
                known = dates != null;
                employment = known ? List.of(new Employment(null, dates.joiningDate(), dates.exitDate())) : List.of();
            } else {
                known = staff.containsKey(person.id());
                StaffEmployment dates = staffDatesById.get(person.id());
                employment = !known
                        ? List.of()
                        : List.of(new Employment(
                                null,
                                dates == null ? null : dates.getJoiningDate(),
                                dates == null ? null : dates.getExitDate()));
            }
            PayableDaysCalculator.Result r = PayableDaysCalculator.compute(month, employment, workingDays);
            int workingInMonth = monthWorkingDays.getOrDefault(r.lastSchoolId(), monthWorkingDays.get(null));
            List<Span> spans = r.spans();
            result.put(
                    person,
                    new Computed(
                            known,
                            new PayableDays(month, r.dates(), workingInMonth, spans),
                            r.schoolByDate(),
                            monthWorkingDays));
        }
        return result;
    }

    // ------------------------------------------------------------ unpaid days

    @Override
    public UnpaidDays unpaidDays(PersonRef person, YearMonth month) {
        if (person.kind() != Kind.TEACHER) {
            // answering "no unpaid days" would silently pay in full (SC-007); spec 032 gives Managers attendance,
            // and staff have none yet
            return new UnpaidDays.Unavailable(notRecorded(person.kind()));
        }
        return unpaid(person, month, compute(List.of(person), month).get(person));
    }

    private UnpaidDays unpaid(PersonRef person, YearMonth month, Computed computed) {
        if (!computed.known()) {
            return new UnpaidDays.Unavailable(UNKNOWN_TEACHER);
        }
        List<MarkView> marks = attendance.marksOf(person.id(), month);
        Set<UUID> requestIds = marks.stream()
                .map(MarkView::leaveRequestId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> lossOfPay = requestIds.isEmpty() ? Set.of() : leaveTypes.lossOfPayRequests(requestIds);
        List<UnpaidDayClassifier.Mark> facts = marks.stream()
                .map(m -> new UnpaidDayClassifier.Mark(m.date(), m.category(), m.dayValue(), m.leaveRequestId()))
                .toList();
        List<UnpaidDay> days = UnpaidDayClassifier.classify(
                facts,
                new HashSet<>(computed.days().dates()),
                lossOfPay,
                policyOfMonth(month).halfDayFraction());
        return new UnpaidDays.Found(days);
    }

    // ------------------------------------------------------- total loss of pay

    @Override
    public TotalLossOfPay totalLossOfPay(PersonRef person, YearMonth month) {
        if (person.kind() != Kind.TEACHER) {
            return new TotalLossOfPay.Cannot(notRecorded(person.kind()));
        }
        Computed computed = compute(List.of(person), month).get(person);
        UnpaidDays unpaid = unpaid(person, month, computed);
        if (unpaid instanceof UnpaidDays.Unavailable unavailable) {
            return new TotalLossOfPay.Cannot(unavailable.reason());
        }
        PayPolicyView policy = policyOfMonth(month);
        List<DayInput> inputs = new ArrayList<>();
        List<UnpaidDayValue> values = new ArrayList<>();
        for (UnpaidDay day : ((UnpaidDays.Found) unpaid).days()) {
            // each unpaid day is valued at the salary in effect on that day, so a mid-month raise splits the month
            SalaryInEffect salary = salaryOn(person, day.date());
            if (salary instanceof SalaryInEffect.NoSalary none) {
                return new TotalLossOfPay.Cannot(
                        "No salary on " + DISPLAY.format(day.date()) + ": " + none.reason().message());
            }
            BigDecimal monthly = ((SalaryInEffect.Found) salary).monthly();
            int workingDays = computed.monthWorkingDaysBySchool()
                    .getOrDefault(computed.schoolByDate().get(day.date()), 0);
            if (workingDays == 0) {
                return new TotalLossOfPay.Cannot(
                        "No working days in " + month + " to value " + DISPLAY.format(day.date()) + ".");
            }
            DayInput input = new DayInput(day.dayValue(), monthly, workingDays);
            inputs.add(input);
            values.add(new UnpaidDayValue(
                    day.date(), day.dayValue(), monthly, workingDays, LossOfPayCalculator.amountOf(input)));
        }
        BigDecimal unrounded = LossOfPayCalculator.sum(inputs);
        return new TotalLossOfPay.Found(LossOfPayCalculator.round(unrounded, policy.rounding()), unrounded, values);
    }

    // ---------------------------------------------------------------- helpers

    private static String notRecorded(Kind kind) {
        return kind == Kind.MANAGER ? MANAGER_ATTENDANCE_NOT_RECORDED : STAFF_ATTENDANCE_NOT_RECORDED;
    }

    private static Set<UUID> idsOf(Collection<PersonRef> persons, Kind kind) {
        return persons.stream()
                .filter(p -> p.kind() == kind)
                .map(PersonRef::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
