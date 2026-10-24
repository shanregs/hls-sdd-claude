package com.hls.training.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InductionBatches {

    Optional<InductionBatchView> batch(UUID id);

    /** Batches that are not cancelled and end on or after the date, earliest start first (links for Training Stay claims). */
    List<InductionBatchView> openFrom(LocalDate today);

    /** Batches that are not cancelled and end between the dates (inclusive), earliest end first. */
    List<InductionBatchView> endingBetween(LocalDate from, LocalDate to);

    /** How many Teachers of the batch were signed off as having completed it. */
    int completedTeachers(UUID batchId);
}
