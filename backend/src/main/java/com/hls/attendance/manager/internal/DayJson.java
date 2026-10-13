package com.hls.attendance.manager.internal;

/** The affected fields of a day as a small JSON text for the history rows (before and after a change). */
final class DayJson {

    private DayJson() {}

    static String snapshot(ManagerDay d) {
        StringBuilder out = new StringBuilder("{");
        field(out, "dayType", d.getDayType() == null ? null : d.getDayType().name());
        field(out, "purpose", d.getPurpose());
        field(out, "checkInAt", d.getCheckInAt() == null ? null : d.getCheckInAt().toString());
        field(out, "checkOutAt", d.getCheckOutAt() == null ? null : d.getCheckOutAt().toString());
        out.append("\"late\":").append(d.isLate()).append(',');
        out.append("\"activities\":[");
        boolean first = true;
        for (ActivityLink a : d.getActivities().stream()
                .sorted(java.util.Comparator.comparing(ActivityLink::getKind).thenComparing(ActivityLink::getActivityId))
                .toList()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(a.getKind() + ":" + a.getActivityId()));
        }
        return out.append("]}").toString();
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append('"').append(name).append("\":").append(value == null ? "null" : quote(value)).append(',');
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
