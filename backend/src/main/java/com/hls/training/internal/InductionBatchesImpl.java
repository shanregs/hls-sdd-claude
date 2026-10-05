package com.hls.training.internal;

import com.hls.training.api.InductionBatchView;
import com.hls.training.api.InductionBatches;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InductionBatchesImpl implements InductionBatches {

    private final InductionBatchRepository batches;

    InductionBatchesImpl(InductionBatchRepository batches) {
        this.batches = batches;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InductionBatchView> batch(UUID id) {
        return batches.findById(id).map(b -> new InductionBatchView(b.getId(), b.getName(), b.getStartsOn(), b.getEndsOn()));
    }
}
