package com.hls.performance.internal;

import java.util.EnumSet;
import java.util.Set;

/** The coded values of the Teacher performance records (spec 026 data-model.md). */
public final class PerformanceTypes {

    private PerformanceTypes() {}

    /** The eight teaching areas of the observation form (FR-002). */
    public enum Area {
        ENGLISH_COMMUNICATION("English communication"),
        TEACHING_ABILITY("Teaching ability"),
        CONTENT_DELIVERY("Content delivery"),
        CLASSROOM_MANAGEMENT("Classroom management"),
        STUDENT_ENGAGEMENT("Student engagement"),
        PREPARATION("Preparation"),
        CONFIDENCE("Confidence"),
        PUNCTUALITY("Punctuality");

        private final String label;

        Area(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Result {
        GOOD("Good"),
        NEEDS_IMPROVEMENT("Needs improvement"),
        UNSUITABLE("Unsuitable");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean needsIssue() {
            return this != GOOD;
        }
    }

    public enum ObservationStatus { DRAFT, SUBMITTED, NOT_HELD, SUPERSEDED }

    public enum NotHeldReason {
        PRINCIPAL_DECLINED("Principal declined"),
        PRINCIPAL_ABSENT("Principal absent"),
        TEACHER_ABSENT("Teacher absent"),
        SCHOOL_CLOSED("School closed"),
        OTHER("Other");

        private final String label;

        NotHeldReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PermissionWay { IN_PERSON, PHONE, MESSAGE, WRITTEN }

    public enum FeedbackCategory {
        GOOD_PERFORMANCE("Good performance"),
        NEEDS_IMPROVEMENT("Needs improvement"),
        ATTENDANCE("Attendance issue"),
        TEACHING("Teaching issue"),
        COMMUNICATION("Communication issue"),
        BEHAVIOUR("Behaviour issue"),
        OTHER("Other concern");

        private final String label;

        FeedbackCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum FeedbackStatus { RECORDED, WITHDRAWN, SUPERSEDED }

    public enum IssueStatus {
        OPEN, UNDER_CORRECTION, REFERRED, RESOLVED, CANCELLED;

        /** An issue that still needs work: at most one per Teacher. */
        public static final Set<IssueStatus> ACTIVE = EnumSet.of(OPEN, UNDER_CORRECTION, REFERRED);
    }

    public enum Resolution { IMPROVED, CONTINUE, TRANSFERRED, REPLACED, EXITED }

    public enum SourceType { OBSERVATION, FEEDBACK }

    public enum PlanStatus { OPEN, DONE, CANCELLED }

    public enum PlanReason {
        SCHOOL_FEEDBACK("School feedback"),
        MANAGEMENT_PLAN("Management plan"),
        FOLLOW_UP("Follow-up"),
        OTHER("Other");

        private final String label;

        PlanReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Recommendation { TRANSFER, REPLACE }

    public enum Outcome { TRANSFERRED, REPLACED }

    /** The task-module source for planned and follow-up observations (spec 025 C2). */
    public static final String TASK_SOURCE = "OBSERVATION_PLAN";

    public static final int MAX_TEXT = 2000;
    public static final int MAX_ATTACHMENTS = 5;
}
