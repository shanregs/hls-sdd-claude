package com.hls.schoolbilling.api;

/** How many positions the School's contract in effect has, how many are filled and how many are vacant. */
public record Occupancy(int positions, int filled, int vacant) {

    public static final Occupancy NONE = new Occupancy(0, 0, 0);
}
