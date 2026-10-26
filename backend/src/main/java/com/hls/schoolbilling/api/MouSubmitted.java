package com.hls.schoolbilling.api;

import java.util.UUID;

/**
 * A MoU was submitted for approval (spec 031). {@code resubmitted} is true for a resubmission after a rejection and for a
 * reset caused by a replaced document. Published in the publisher's transaction; {@code notification} listens.
 */
public record MouSubmitted(UUID contractId, UUID schoolId, UUID submittedBy, boolean resubmitted) {}
