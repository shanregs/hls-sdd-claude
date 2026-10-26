package com.hls.training.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A small read of training sessions for other specs (spec 015 links a Training Stay claim to a session). Only Published and
 * Held sessions are returned; no roster, trainer, outcome or remark is exposed.
 */
public interface TrainingSessions {

    /** The facts a claim needs to link and label a session. */
    record SessionView(UUID id, String topic, LocalDate date, String type) {}

    /** Published and held sessions dated in the range, earliest first. */
    List<SessionView> between(LocalDate from, LocalDate to);

    /** One Published or Held session, or empty. */
    Optional<SessionView> session(UUID id);
}
