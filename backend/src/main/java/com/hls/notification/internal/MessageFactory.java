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
