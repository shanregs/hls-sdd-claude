package com.hls.notification.internal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.SortedSet;
import java.util.UUID;

/**
 * Builds the title, message and link of every notification (spec 010 contracts/notifications-api.md).
 * Pure text: no Spring, no database. Dates read DD/MM/YYYY and reasons are shortened to 200 characters.
 */
public final class MessageFactory {

    public static final int MAX_TITLE = 80;
    public static final int MAX_MESSAGE = 300;
    public static final int MAX_LINK = 200;
    public static final int MAX_REASON = 200;

    private MessageFactory() {}

    /** What a notification says and where it leads. */
    public record Text(String title, String message, String link) {}

    public static Text leaveApproved(LocalDate first, LocalDate last) {
        return new Text("Your leave was approved", "Your leave " + range(first, last) + " was approved.", "/leave/history");
    }

    public static Text leaveRejected(LocalDate first, LocalDate last, String reason) {
        return new Text(
                "Your leave was rejected",
                "Your leave " + range(first, last) + " was rejected" + because(reason) + ".",
                "/leave/history");
    }

    public static Text leaveRevoked(LocalDate first, LocalDate last, String reason) {
        return new Text(
                "Your approved leave was revoked",
                "Your approved leave " + range(first, last) + " was revoked" + because(reason) + ".",
                "/leave/history");
    }

    public static Text leaveRequested(String teacherName, LocalDate first, LocalDate last) {
        return new Text(
                "New leave request", teacherName + " asked for leave " + range(first, last) + ".", "/operations/leave");
    }

    public static Text leaveCancelled(String teacherName, LocalDate first, LocalDate last) {
        return new Text(
                "Leave request withdrawn",
                teacherName + " cancelled their leave " + range(first, last) + ".",
                "/operations/leave");
    }

    /** One changed day reads "updated your attendance for 05/10/2026"; several give a day count. */
    public static Text attendanceChanged(String actorName, SortedSet<LocalDate> days, YearMonth month) {
        String message = days.size() == 1
                ? actorName + " updated your attendance for " + date(days.first()) + "."
                : actorName + " updated " + days.size() + " days of your attendance in " + monthName(month) + ".";
        return new Text("Your attendance was updated", message, attendanceLink(month));
    }

    public static Text mouNotRecorded(String prospectName, int days, UUID prospectId) {
        return new Text(
                shorten("MoU not recorded: " + prospectName, MAX_TITLE),
                shorten(prospectName + " was won " + days + " days ago and its MoU is still not recorded.", MAX_MESSAGE),
                "/marketing/prospects/" + prospectId);
    }

    public static Text taskAssigned(String title, LocalDate dueOn, UUID taskId) {
        return new Text(
                shorten("New task: " + title, MAX_TITLE),
                shorten("You were given the task \"" + title + "\", due " + date(dueOn) + ".", MAX_MESSAGE),
                "/operations/tasks/" + taskId);
    }

    public static Text schoolDueUnassigned(String school, String month, java.math.BigDecimal balance, long daysOverdue, java.util.UUID schoolId) {
        return new Text(
                shorten("Overdue payment: " + school, MAX_TITLE),
                shorten(school + " owes " + balance.toPlainString() + " for " + month + " and is " + daysOverdue
                        + " days past the due date. The School has no Zone Manager to follow up.", MAX_MESSAGE),
                "/operations/school-billing?schoolId=" + schoolId);
    }

    public static Text taskOverdue(String title, LocalDate dueOn, UUID taskId) {
        return new Text(
                shorten("Task overdue: " + title, MAX_TITLE),
                shorten("\"" + title + "\" was due on " + date(dueOn) + ". Record why it is late and finish it.", MAX_MESSAGE),
                "/operations/tasks/" + taskId);
    }

    public static Text taskEscalated(String title, String managerName, LocalDate dueOn, int daysOverdue, UUID taskId) {
        return new Text(
                shorten("Task escalated: " + title, MAX_TITLE),
                shorten(managerName + "'s task \"" + title + "\" was due on " + date(dueOn) + " and is " + daysOverdue + " days overdue.", MAX_MESSAGE),
                "/operations/tasks/" + taskId);
    }

    public static Text observationPlanned(String teacherName, LocalDate plannedOn, String reason) {
        return new Text(
                shorten("Observation planned: " + teacherName, MAX_TITLE),
                shorten("Observe " + teacherName + " on " + date(plannedOn) + " (" + reason + ").", MAX_MESSAGE),
                "/operations/teacher-performance/planned");
    }

    private static String movement(String kind) {
        return "REPLACEMENT".equals(kind) ? "replacement" : "transfer";
    }

    public static Text mouSubmitted(String school, boolean resubmitted) {
        return new Text(
                shorten("MoU for approval: " + school, MAX_TITLE),
                shorten("The MoU of " + school + (resubmitted ? " was sent for approval again." : " was sent for approval."), MAX_MESSAGE),
                "/operations/school-contracts/schools/");
    }

    public static Text mouDecided(String school, String outcome, String reason, boolean termsAmended) {
        String verb = outcome.toLowerCase(Locale.ROOT);
        String amended = termsAmended ? " The approver corrected some terms." : "";
        return new Text(
                shorten("MoU " + verb + ": " + school, MAX_TITLE),
                shorten("The MoU of " + school + " was " + verb + because(reason) + "." + amended, MAX_MESSAGE),
                "/operations/school-contracts/schools/");
    }

    public static Text transferRequested(String kind, String teacherName, String fromSchool, LocalDate on) {
        return new Text(
                shorten("New " + movement(kind) + " request: " + teacherName, MAX_TITLE),
                shorten(teacherName + " at " + fromSchool + ", effective " + date(on) + ".", MAX_MESSAGE),
                "/operations/transfers");
    }

    public static Text transferDecided(String kind, String outcome, String teacherName, LocalDate on, UUID requestId) {
        String verb = outcome.toLowerCase();
        return new Text(
                shorten("The " + movement(kind) + " for " + teacherName + " was " + verb, MAX_TITLE),
                shorten("The " + movement(kind) + " for " + teacherName + " (effective " + date(on) + ") was " + verb + ".", MAX_MESSAGE),
                "/operations/transfers/" + requestId);
    }

    public static Text transferApplied(String kind, String teacherName, LocalDate on, UUID requestId) {
        return new Text(
                shorten("The " + movement(kind) + " for " + teacherName + " took effect", MAX_TITLE),
                shorten("The " + movement(kind) + " for " + teacherName + " took effect on " + date(on) + ".", MAX_MESSAGE),
                "/operations/transfers/" + requestId);
    }

    public static Text referralRecorded(String teacherName, String recommendation, UUID issueId) {
        return new Text(
                shorten("Teacher referred: " + teacherName, MAX_TITLE),
                shorten(teacherName + " was referred for " + ("TRANSFER".equals(recommendation) ? "transfer" : "replacement") + ". Please review.", MAX_MESSAGE),
                "/operations/teacher-performance/issues/" + issueId);
    }

    /** Where a Teacher or Manager reaches their own training; the screen sends each role to its own page (spec 024). */
    static final String MY_TRAINING_LINK = "/training/mine";

    public static Text trainingAssigned(String topic, LocalDate on) {
        return new Text(
                "You are required at a training session",
                shorten("You are required at the training \"" + topic + "\" on " + date(on) + ".", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingAssignmentRemoved(String topic, LocalDate on) {
        return new Text(
                "You no longer need to attend a training",
                shorten("You no longer need to attend the training \"" + topic + "\" on " + date(on) + ".", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingMoved(String topic, LocalDate oldDate, LocalDate newDate) {
        return new Text(
                "A training session was moved",
                shorten("The training \"" + topic + "\" moved from " + date(oldDate) + " to " + date(newDate) + ".", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingCancelled(String topic, LocalDate on) {
        return new Text(
                "A training session was cancelled",
                shorten("The training \"" + topic + "\" on " + date(on) + " was cancelled.", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingReminder(String topic, LocalDate on, int daysBefore) {
        String when = daysBefore <= 0 ? "today" : daysBefore == 1 ? "tomorrow" : "in " + daysBefore + " days (" + date(on) + ")";
        return new Text(
                "Training reminder",
                shorten("The training \"" + topic + "\" is " + when + ".", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingRegistered(String topic, LocalDate on) {
        return new Text(
                "Your training registration is confirmed",
                shorten("You are registered for the training \"" + topic + "\" on " + date(on) + ".", MAX_MESSAGE),
                MY_TRAINING_LINK);
    }

    public static Text trainingException(String personName, LocalDate on, UUID sessionId) {
        return new Text(
                shorten("Training absence: " + personName, MAX_TITLE),
                shorten(personName + " was absent from the training on " + date(on) + ".", MAX_MESSAGE),
                "/operations/training/exceptions?session=" + sessionId);
    }

    public static Text trainingCannotAttend(String personName, String note, UUID sessionId) {
        return new Text(
                shorten(personName + " cannot attend a training", MAX_TITLE),
                shorten(personName + " says they cannot attend" + because(note) + ".", MAX_MESSAGE),
                "/operations/training/sessions/" + sessionId);
    }

    public static Text substituteNeeded(String teacherName, String schoolName, LocalDate day, UUID requirementId) {
        return new Text(
                "Substitute needed",
                teacherName + " is away on " + date(day) + " and " + schoolName + " needs a substitute.",
                "/operations/substitutions/" + requirementId);
    }

    public static Text substituteAssigned(String schoolName, LocalDate day) {
        return new Text(
                "You are covering a class",
                "You were assigned to cover a class at " + schoolName + " on " + date(day) + ".",
                "/my-substitutions");
    }

    public static Text substituteEnded(String schoolName, LocalDate day, String why) {
        return new Text(
                "Your cover was cancelled",
                "Your cover at " + schoolName + " on " + date(day) + " is no longer needed" + because(why) + ".",
                "/my-substitutions");
    }

    public static Text schoolInformed(String schoolName, LocalDate day, UUID requirementId) {
        return new Text(
                "School informed",
                schoolName + " was informed about the cover on " + date(day) + ".",
                "/operations/substitutions/" + requirementId);
    }

    public static Text managerDayCorrected(LocalDate day) {
        return new Text(
                "Your attendance was corrected",
                "Your attendance for " + date(day) + " was corrected by the office.",
                "/manager-attendance/me/month");
    }

    public static Text managerRegularizationRequested(String managerName, LocalDate day) {
        return new Text(
                shorten("Forgotten check-in: " + managerName, MAX_TITLE),
                shorten(managerName + " asked to add a missing check-in for " + date(day) + ".", MAX_MESSAGE),
                "/operations/manager-attendance");
    }

    public static Text managerRegularizationDecided(LocalDate day, boolean approved, String reason) {
        return new Text(
                approved ? "Check-in approved" : "Check-in rejected",
                "Your request to add a check-in for " + date(day) + (approved ? " was approved." : " was rejected" + because(reason) + "."),
                "/manager-attendance/me/month");
    }

    /** The Manager is told to check in; the Admins and Directors are told who has not. */
    public static Text managerCheckInMissed(String managerName, LocalDate day, boolean forManager) {
        if (forManager) {
            return new Text(
                    "You have not checked in",
                    "You have not checked in for " + date(day) + ". Check in now, or ask for leave.",
                    "/manager-attendance/me");
        }
        return new Text(
                shorten("Not checked in: " + managerName, MAX_TITLE),
                shorten(managerName + " has not checked in for " + date(day) + ".", MAX_MESSAGE),
                "/operations/manager-attendance");
    }

    public static Text managerLeaveRequested(String managerName, LocalDate first, LocalDate last) {
        return new Text(
                shorten("Leave request: " + managerName, MAX_TITLE),
                shorten(managerName + " asked for leave " + range(first, last) + ".", MAX_MESSAGE),
                "/operations/manager-leave");
    }

    public static Text managerLeaveCancelled(String managerName, LocalDate first, LocalDate last) {
        return new Text(
                "Leave request withdrawn",
                shorten(managerName + " cancelled their leave " + range(first, last) + ".", MAX_MESSAGE),
                "/operations/manager-leave");
    }

    public static Text managerLeaveDecided(String decision, LocalDate first, LocalDate last, String reason) {
        String title = switch (decision) {
            case "APPROVED" -> "Your leave was approved";
            case "REVOKED" -> "Your approved leave was revoked";
            default -> "Your leave was rejected";
        };
        String verb = switch (decision) {
            case "APPROVED" -> "was approved";
            case "REVOKED" -> "was revoked";
            default -> "was rejected";
        };
        return new Text(
                title,
                shorten("Your leave " + range(first, last) + " " + verb + ("APPROVED".equals(decision) ? "" : because(reason)) + ".", MAX_MESSAGE),
                "/manager-attendance/me/leave");
    }

    public static Text monthLocked(YearMonth month) {
        return new Text(
                "Your attendance month was locked",
                monthName(month) + " is locked; changes now need a reopen.",
                attendanceLink(month));
    }

    public static Text monthReopened(YearMonth month, String reason) {
        return new Text(
                "Your attendance month was reopened",
                monthName(month) + " was reopened" + because(reason) + ".",
                attendanceLink(month));
    }

    /** A pay run waits for approval. The month and the status only, never an amount (spec 013b). */
    public static Text payrollRunSubmitted(UUID runId, YearMonth month) {
        return new Text(
                "Pay run awaiting approval",
                "The pay run for " + monthName(month) + " was submitted and is awaiting approval.",
                "/operations/payroll/" + runId);
    }

    public static Text payrollRunApproved(UUID runId, YearMonth month) {
        return new Text(
                "Pay run approved",
                "The pay run for " + monthName(month) + " was approved and is now final.",
                "/operations/payroll/" + runId);
    }

    public static Text payrollRunReturned(UUID runId, YearMonth month, String reason) {
        return new Text(
                "Pay run returned",
                "The pay run for " + monthName(month) + " was returned to draft" + because(reason) + ".",
                "/operations/payroll/" + runId);
    }

    /** A claim waits for review (spec 015). Names the claimant, never an amount. */
    public static Text expenseSubmitted(UUID claimId, String claimantName, boolean resubmitted) {
        return new Text(
                resubmitted ? "Expense claim resubmitted" : "Expense claim submitted",
                claimantName + (resubmitted ? " resubmitted" : " submitted") + " an expense claim for review.",
                "/operations/expenses/" + claimId);
    }

    public static Text expenseDecided(UUID claimId, String outcome, String reason) {
        String what = "APPROVED".equals(outcome) ? "approved" : "rejected";
        return new Text(
                "Expense claim " + what,
                "Your expense claim was " + what + because(reason) + ".",
                "/operations/my-expenses/" + claimId);
    }

    public static Text expenseReversed(UUID claimId, String reason) {
        return new Text(
                "Expense approval reversed",
                "The approval of your expense claim was reversed and it is back with the reviewers" + because(reason) + ".",
                "/operations/my-expenses/" + claimId);
    }

    public static String attendanceLink(YearMonth month) {
        return "/my-attendance?month=" + month;
    }

    public static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    static String date(LocalDate date) {
        return String.format("%02d/%02d/%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    static String range(LocalDate first, LocalDate last) {
        return first.equals(last) ? date(first) : date(first) + " to " + date(last);
    }

    private static String because(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return ": " + shorten(reason.trim(), MAX_REASON);
    }

    /** Cuts text to {@code max} characters, ending with an ellipsis when something was removed. */
    public static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
    }
}
