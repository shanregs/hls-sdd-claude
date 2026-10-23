package com.hls.performance.internal;

import com.hls.school.api.InvalidInputException;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskCreation.TaskRef;
import com.hls.tasks.api.TaskType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place the performance module talks to the task module (spec 025 C2). An observation plan is an Observation task
 * addressed by the plan's own key, so the plan never needs the task's id.
 */
@Component
class TaskBridge {

    private final TaskCreation tasks;

    TaskBridge(TaskCreation tasks) {
        this.tasks = tasks;
    }

    /** Creates the Observation task for the plan's Manager; a Teacher with no Manager is refused with the reason. */
    TaskRef create(ObservationPlan plan, TeacherFacts.TeacherContext teacher, String reasonLabel, UUID createdBy) {
        if (teacher.managerUserId() == null) {
            throw new InvalidInputException("This Teacher's School has no Manager yet, so there is nobody to give the observation to.");
        }
        return tasks.createFollowUp(new FollowUpRequest(
                TaskType.OBSERVATION,
                title(teacher),
                "Observe " + teacher.name() + " at " + teacher.schoolName() + ". Reason: " + reasonLabel + ".",
                teacher.managerUserId(),
                plan.plannedOn,
                null,
                "TEACHER",
                teacher.teacherId(),
                teacher.name(),
                teacher.schoolName(),
                plan.taskSource,
                plan.taskSourceKey,
                createdBy));
    }

    boolean reschedule(ObservationPlan plan, TeacherFacts.TeacherContext teacher, String reasonLabel) {
        return tasks.reschedule(
                plan.taskSource,
                plan.taskSourceKey,
                plan.plannedOn,
                title(teacher),
                "Observe " + teacher.name() + " at " + teacher.schoolName() + ". Reason: " + reasonLabel + ".");
    }

    boolean complete(ObservationPlan plan, UUID completedBy, String note) {
        return tasks.complete(plan.taskSource, plan.taskSourceKey, note, completedBy);
    }

    boolean cancel(ObservationPlan plan, String reason) {
        return tasks.cancel(plan.taskSource, plan.taskSourceKey, reason);
    }

    boolean reassign(ObservationPlan plan, UUID managerUserId) {
        return tasks.reassign(plan.taskSource, plan.taskSourceKey, managerUserId);
    }

    private static String title(TeacherFacts.TeacherContext teacher) {
        String title = "Observe " + teacher.name();
        return title.length() > 120 ? title.substring(0, 120) : title;
    }
}
