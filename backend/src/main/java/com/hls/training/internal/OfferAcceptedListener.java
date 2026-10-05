package com.hls.training.internal;

import com.hls.recruitment.api.OfferAccepted;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrols a newly hired recruit in the next batch with room, inside the acceptance transaction. With no batch with room
 * the recruit simply waits in the "to be enrolled" list. Recruitment never calls training; this listener is the link.
 */
@Component
class OfferAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(OfferAcceptedListener.class);

    private final BatchService batchService;
    private final InductionBatchRepository batches;

    OfferAcceptedListener(BatchService batchService, InductionBatchRepository batches) {
        this.batchService = batchService;
        this.batches = batches;
    }

    @EventListener
    @Transactional
    void on(OfferAccepted event) {
        Optional<InductionBatch> next = batchService.nextWithRoom(null);
        if (next.isEmpty()) {
            return;
        }
        // lock the batch, then re-read the seat count through enrolLocked so the last seat goes to one request only
        InductionBatch batch = batches.findForUpdate(next.get().getId()).orElse(null);
        if (batch == null) {
            return;
        }
        try {
            batchService.enrolLocked(event.acceptedBy(), batch, event.teacherId());
        } catch (RuntimeException e) {
            log.info("Recruit {} was not enrolled automatically: {}", event.teacherId(), e.getMessage());
        }
    }
}
