package com.hls.recruitment.api;

import java.util.OptionalInt;

/**
 * How many recruits are ready to deploy, for the demand-against-supply figure of the marketing dashboard. Defined here
 * and provided by the induction part of spec 016 in a follow-up; without a provider the dashboard shows "not
 * available".
 */
public interface SupplySource {

    OptionalInt readyToDeployCount();
}
