package com.hls.expenses.internal;

/** The six expense categories (spec 015 FR-002). */
public enum ExpenseCategory {
    RENT_UTILITIES("Rent and Utilities"),
    TRAVEL_FUEL("Travel and Fuel"),
    TRAINING_STAY("Training Stay"),
    MARKETING("Marketing"),
    OFFICE_SUPPLIES("Office Supplies"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
