package com.hls.expenses.internal;

import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import com.hls.school.api.InvalidInputException;
import com.hls.tasks.api.TaskActivities;
import com.hls.training.api.InductionBatchView;
import com.hls.training.api.InductionBatches;
import com.hls.training.api.TrainingSessions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What a claim may be linked to (spec 015 FR-010, FR-011, research 9): the claimant's own planned activities near the
 * claim date for Travel and Fuel, induction batches for Training Stay. A chosen link must be one of the offered set; it
 * is stored as kind, id, date and label so it survives the activity being cancelled later.
 */
@Component
class LinkResolver {

    /** Activities this many days either side of the claim date are offered. */
    static final int WINDOW_DAYS = 7;
    private static final int MAX_LABEL = 200;

    /** One thing a claim can be linked to. */
    record Option(String kind, UUID ref, LocalDate date, String label) {}

    private final DriveActivities drives;
    private final MarketingActivities marketing;
    private final TaskActivities tasks;
    private final InductionBatches batches;
    private final TrainingSessions trainingSessions;

    LinkResolver(
            DriveActivities drives,
            MarketingActivities marketing,
            TaskActivities tasks,
            InductionBatches batches,
            TrainingSessions trainingSessions) {
        this.drives = drives;
        this.marketing = marketing;
        this.tasks = tasks;
        this.batches = batches;
        this.trainingSessions = trainingSessions;
    }

    /** The links the claimant may choose for the category and date; empty for a category that takes none. */
    List<Option> options(UUID claimantId, ExpenseCategory category, LocalDate date) {
        List<Option> options = new ArrayList<>();
        if (category == ExpenseCategory.TRAVEL_FUEL) {
            LocalDate from = date.minusDays(WINDOW_DAYS);
            LocalDate to = date.plusDays(WINDOW_DAYS);
            for (PlannedActivity a : drives.plannedBetween(from, to, claimantId)) {
                options.add(option(a.kind(), a.id(), a.date(), a.place()));
            }
            for (PlannedActivity a : marketing.plannedBetween(from, to, claimantId)) {
                options.add(option(a.kind(), a.id(), a.date(), a.place()));
            }
            for (TaskActivities.PlannedActivity a : tasks.plannedBetween(from, to, claimantId)) {
                options.add(option(LinkKinds.TASK, a.id(), a.date(), a.place() == null ? a.kind() : a.place()));
            }
            options.sort(Comparator.comparing(Option::date).thenComparing(Option::label));
        } else if (category == ExpenseCategory.TRAINING_STAY) {
            for (InductionBatchView b : batches.endingBetween(date.minusDays(120), date.plusDays(365))) {
                options.add(batchOption(b));
            }
            for (TrainingSessions.SessionView t : trainingSessions.between(date.minusDays(120), date.plusDays(365))) {
                options.add(sessionOption(t));
            }
        }
        return options;
    }

    /**
     * Checks a chosen link against the category and returns the snapshot to store, or empty when no link was chosen.
     * A category that takes no link refuses one; an unknown link is refused.
     */
    Optional<Option> resolve(UUID claimantId, ExpenseCategory category, LocalDate date, String kind, UUID ref) {
        boolean chosen = kind != null && !kind.isBlank() || ref != null;
        if (!chosen) {
            return Optional.empty();
        }
        if (kind == null || kind.isBlank() || ref == null) {
            throw new InvalidInputException("A link needs both its kind and its reference.");
        }
        if (category != ExpenseCategory.TRAVEL_FUEL && category != ExpenseCategory.TRAINING_STAY) {
            throw new InvalidInputException("This category does not take a link.");
        }
        String wanted = kind.trim();
        if (category == ExpenseCategory.TRAINING_STAY) {
            if (LinkKinds.TRAINING_SESSION.equals(wanted)) {
                return Optional.of(trainingSessions.session(ref)
                        .map(LinkResolver::sessionOption)
                        .orElseThrow(() -> new InvalidInputException("That training session does not exist.")));
            }
            if (!LinkKinds.INDUCTION_BATCH.equals(wanted)) {
                throw new InvalidInputException("A Training Stay claim links to an induction batch or a training session.");
            }
            return Optional.of(batches.batch(ref)
                    .map(LinkResolver::batchOption)
                    .orElseThrow(() -> new InvalidInputException("That induction batch does not exist.")));
        }
        if (LinkKinds.INDUCTION_BATCH.equals(wanted) || LinkKinds.TRAINING_SESSION.equals(wanted)) {
            throw new InvalidInputException("Travel and Fuel links to a visit, meeting, drive or task, not a batch or a training session.");
        }
        return options(claimantId, category, date).stream()
                .filter(o -> o.kind().equals(wanted) && o.ref().equals(ref))
                .findFirst()
                .map(Optional::of)
                .orElseThrow(() -> new InvalidInputException(
                        "That activity is not one of yours near the claim date, so it cannot be linked."));
    }

    private static Option option(String kind, UUID id, LocalDate date, String place) {
        String label = LinkKinds.label(kind) + (place == null || place.isBlank() ? "" : " - " + place.trim());
        return new Option(kind, id, date, shorten(label));
    }

    private static Option batchOption(InductionBatchView b) {
        return new Option(
                LinkKinds.INDUCTION_BATCH, b.id(), b.startsOn(), shorten(b.name() + " (" + b.startsOn() + " to " + b.endsOn() + ")"));
    }

    private static Option sessionOption(TrainingSessions.SessionView t) {
        return new Option(LinkKinds.TRAINING_SESSION, t.id(), t.date(), shorten("Training session - " + t.topic() + " (" + t.date() + ")"));
    }

    private static String shorten(String label) {
        return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL - 1) + "…";
    }
}
