package com.hls.incentive.api;

/**
 * The runtime setting of the incentive module (spec 030), owned by {@code incentive} and implemented by {@code settings}
 * (the same direction as {@code TrainingSettings}), so incentive never reads the settings module.
 */
public interface IncentiveSettings {

    /** Rupees paid for each closed School, read when an incentive is created (default 5000). */
    int amountPerSchool();
}
