package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The monthly salary of a person on a date, or the stated reason there is none. Never zero for "missing". */
public sealed interface SalaryInEffect {

    enum Source {
        /** A Manager's salary: the structure of their designation. */
        DESIGNATION,
        /** A Teacher's salary: the amount on their own record (spec 005). */
        TEACHER_RECORD
    }

    enum Reason {
        NO_DESIGNATION("The Manager has no designation on this date."),
        DESIGNATION_HAS_NO_SALARY("The designation has no salary recorded on or before this date."),
        TEACHER_HAS_NO_RECORDED_SALARY("The Teacher has no salary recorded on their own record."),
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
