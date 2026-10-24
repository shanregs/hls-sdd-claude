package com.hls.training.api;

/**
 * The runtime settings of the training calendar (spec 024), owned by {@code training} and implemented by {@code settings}
 * (the same direction as {@code RuntimeSettings}), so training never reads the settings module.
 */
public interface TrainingSettings {

    /** Unexcused absences in the window at or over which a person is marked as a repeat (default 2). */
    int repeatThreshold();

    /** Months looked back when counting repeat absences (default 6). */
    int repeatWindowMonths();

    /** Days before a session for the first reminder (default 2). */
    int reminderDaysFirst();

    /** Days before a session for the second reminder; 0 turns it off (default 1). */
    int reminderDaysSecond();

    /** Days after a session within which attendance should be recorded (default 7). */
    int attendanceDueDays();

    /** How many months of monthly sessions are kept ahead of the current month (default 3). */
    int generationHorizonMonths();
}
