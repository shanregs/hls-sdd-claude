package com.hls.training.calendar.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily 03:10 in the business zone: keeps the monthly sessions of the standing patterns supplied up to the horizon. */
@Component("trainingGenerationJob")
public class GenerationJob {

    private static final Logger log = LoggerFactory.getLogger(GenerationJob.class);

    private final GenerationService generation;
    private final boolean enabled;

    GenerationJob(GenerationService generation, @Value("${hls.training.generation-job.enabled:true}") boolean enabled) {
        this.generation = generation;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 10 3 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Training generation created {} sessions", generation.runToday());
        } catch (RuntimeException e) {
            log.error("Training generation failed", e);
        }
    }
}
