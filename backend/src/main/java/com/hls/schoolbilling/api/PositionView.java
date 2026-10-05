package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.util.UUID;

/** One Teacher position of a contract and the monthly salary the School pays for it. */
public record PositionView(UUID id, int number, String title, BigDecimal salary) {}
