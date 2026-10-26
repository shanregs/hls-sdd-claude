package com.hls.expenses.internal;

import java.util.Map;
import java.util.Set;

/**
 * The link kinds a claim can store (spec 015 FR-010, FR-011). Marketing and drive kinds are the strings the recruitment
 * module writes into its planned activities (contract C1); tasks are all stored as {@code TASK}. One place, used by the
 * link selector and by the cost per School measure.
 */
final class LinkKinds {

    static final String INDUCTION_BATCH = "INDUCTION_BATCH";
    static final String TRAINING_SESSION = "TRAINING_SESSION";
    static final String TASK = "TASK";
    static final String CAMPUS_DRIVE = "CAMPUS_DRIVE";

    /** Marketing activity kinds: "SCHOOL_" plus the activity type (visit, call, proposal meeting, follow-up). */
    static final Set<String> MARKETING = Set.of("SCHOOL_VISIT", "SCHOOL_CALL", "SCHOOL_PROPOSAL_MEETING", "SCHOOL_FOLLOW_UP");

    /** Kinds whose Travel and Fuel cost counts toward winning a School: marketing work and campus drives. */
    static final Set<String> ACQUISITION = Set.of(
            "SCHOOL_VISIT", "SCHOOL_CALL", "SCHOOL_PROPOSAL_MEETING", "SCHOOL_FOLLOW_UP", "CAMPUS_DRIVE");

    private static final Map<String, String> LABELS = Map.of(
            "SCHOOL_VISIT", "School visit",
            "SCHOOL_CALL", "School call",
            "SCHOOL_PROPOSAL_MEETING", "Proposal meeting",
            "SCHOOL_FOLLOW_UP", "School follow-up",
            CAMPUS_DRIVE, "Campus drive",
            TASK, "Task",
            INDUCTION_BATCH, "Induction batch",
            TRAINING_SESSION, "Training session");

    private LinkKinds() {}

    static String label(String kind) {
        return LABELS.getOrDefault(kind, kind);
    }
}
