package com.hls.attendance.workingdays.internal;

/** The three refusals of a write that carry a row for the screen to show (contracts/school-working-days-api.md). */
public final class WorkingDaysConflicts {

    private WorkingDaysConflicts() {}

    /** Another row already exists for this School and month: the screen offers to edit it. Maps to 409 DUPLICATE. */
    public static class Duplicate extends RuntimeException {
        private final transient SchoolWorkingDaysRow existing;

        public Duplicate(SchoolWorkingDaysRow existing) {
            super("A figure already exists for this School and month. Edit it instead.");
            this.existing = existing;
        }

        public SchoolWorkingDaysRow existing() {
            return existing;
        }
    }

    /** The row changed since it was loaded. Maps to 409 STALE with the current row. */
    public static class Stale extends RuntimeException {
        private final transient SchoolWorkingDaysRow current;

        public Stale(SchoolWorkingDaysRow current) {
            super("This figure was changed by someone else. The current figure is shown.");
            this.current = current;
        }

        public SchoolWorkingDaysRow current() {
            return current;
        }
    }

    /** The row was deleted by someone else; nothing is recreated. Maps to 404 ROW_DELETED. */
    public static class RowDeleted extends RuntimeException {
        public RowDeleted() {
            super("This figure was deleted by someone else.");
        }
    }
}
