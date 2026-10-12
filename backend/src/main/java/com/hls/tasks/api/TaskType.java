package com.hls.tasks.api;

/** The kinds of Manager task named in the deck (spec 025 FR-001). */
public enum TaskType {
    SCHOOL_VISIT("School visit"),
    FOLLOW_UP("Follow-up"),
    CONTENT_SUBMISSION("Content preparation and submission"),
    RECRUITMENT("Recruitment"),
    TRAINING("Training"),
    TEACHER_ASSIGNMENT("Teacher assignment"),
    OBSERVATION("Observation"),
    PRINCIPAL_FEEDBACK("Principal feedback"),
    SUBSTITUTE_ARRANGEMENT("Substitute arrangement");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
