package com.hls.training.calendar.internal;

import com.hls.school.api.InvalidInputException;
import java.net.URI;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * The plain rules of a training session (spec 024 data-model.md): which status may change to which, what may be edited,
 * moved, cancelled or deleted in each, the time order, the https-only virtual venue link and the text caps. No Spring and
 * no database, so the rules are tested alone.
 */
public final class SessionRules {

    public static final int TOPIC_MAX = 200;
    public static final int TRAINER_NAME_MAX = 120;
    public static final int TRAINER_CONTACT_MAX = 160;
    public static final int VENUE_MAX = 300;
    public static final int DESCRIPTION_MAX = 1000;
    public static final int REASON_MAX = 300;
    public static final int REMARK_MAX = 500;

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            TrainingSession.DRAFT, Set.of(TrainingSession.PUBLISHED),
            TrainingSession.PUBLISHED, Set.of(TrainingSession.HELD, TrainingSession.CANCELLED, TrainingSession.SKIPPED),
            TrainingSession.SKIPPED, Set.of(TrainingSession.PUBLISHED),
            TrainingSession.HELD, Set.of(),
            TrainingSession.CANCELLED, Set.of());

    private SessionRules() {}

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** A session can be edited (topic, trainer, venue, seats, date and time) only while it is a Draft or Published. */
    public static boolean editable(String status) {
        return TrainingSession.DRAFT.equals(status) || TrainingSession.PUBLISHED.equals(status);
    }

    /** A date or time move is an edit of a Published session (it needs a reason and writes history). */
    public static boolean movable(String status) {
        return TrainingSession.PUBLISHED.equals(status);
    }

    public static boolean publishable(String status) {
        return TrainingSession.DRAFT.equals(status);
    }

    public static boolean cancellable(String status) {
        return TrainingSession.PUBLISHED.equals(status);
    }

    /** Only a Draft can be deleted; anything else has been shown to people and is kept as history. */
    public static boolean deletable(String status) {
        return TrainingSession.DRAFT.equals(status);
    }

    /** People can be assigned or register only for a Published session. */
    public static boolean takesPeople(String status) {
        return TrainingSession.PUBLISHED.equals(status);
    }

    public static void checkTimes(LocalTime startsAt, LocalTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new InvalidInputException("The start and end time are required.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidInputException("The end time must be after the start time.");
        }
    }

    /** A virtual venue must be an https link; a physical venue is free text. */
    public static void checkVenue(String venueKind, String venue) {
        if (!"PHYSICAL".equals(venueKind) && !"VIRTUAL".equals(venueKind)) {
            throw new InvalidInputException("The venue must be physical or virtual.");
        }
        checkMax("The venue", venue, VENUE_MAX);
        if ("VIRTUAL".equals(venueKind) && venue != null && !venue.isBlank() && !isHttps(venue)) {
            throw new InvalidInputException("A virtual venue must be an https link.");
        }
    }

    public static boolean isHttps(String link) {
        if (link == null) {
            return false;
        }
        try {
            URI uri = URI.create(link.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String required(String label, String value, int max) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(label + " is required.");
        }
        checkMax(label, value, max);
        return value.trim();
    }

    public static String optional(String label, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        checkMax(label, value, max);
        return value.trim();
    }

    public static void checkMax(String label, String value, int max) {
        if (value != null && value.trim().length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
    }
}
