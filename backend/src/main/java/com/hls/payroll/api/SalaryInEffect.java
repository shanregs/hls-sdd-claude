package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The monthly salary of a person on a date, or the stated reason there is none. Never zero for "missing". */
public sealed interface SalaryInEffect {

    enum Source {
        /** A Teacher's salary: the amount on their own record (spec 005). */
        TEACHER_RECORD,
        /** A Manager's or staff member's salary: the history recorded for that person in spec 013a. */
        PERSON_RECORD
    }

    enum Reason {
        TEACHER_HAS_NO_RECORDED_SALARY("The Teacher has no salary recorded on their own record."),
        PERSON_HAS_NO_RECORDED_SALARY("No salary has been recorded for this person on or before this date."),
        NOT_EMPLOYED("The person is not employed on this date.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    record Found(BigDecimal monthly, LocalDate effectiveOn, Source source) implements SalaryInEffect {}

    record NoSalary(Reason reason) implements SalaryInEffect {}
}
