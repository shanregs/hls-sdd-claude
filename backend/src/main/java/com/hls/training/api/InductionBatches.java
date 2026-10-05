package com.hls.training.api;

import java.util.Optional;
import java.util.UUID;

public interface InductionBatches {

    Optional<InductionBatchView> batch(UUID id);
}
