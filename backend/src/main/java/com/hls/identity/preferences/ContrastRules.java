package com.hls.identity.preferences;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * WCAG contrast maths for spec 039 (research.md sections 3 and 4). Pure and deterministic: the web and
 * mobile clients carry the same algorithm and are tested against the same vectors
 * ({@code specs/039-user-themes/contracts/contrast-vectors.json}).
 */
public final class ContrastRules {

    /** AA for normal text. */
    public static final double MIN_TEXT_RATIO = 4.5;

    public static final String WHITE = "#FFFFFF";
    public static final String DARK_HEADER_TEXT = "#0B1B33";

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private ContrastRules() {}

    public static boolean isHex(String value) {
        return value != null && HEX.matcher(value).matches();
    }

    public static String normalise(String hex) {
        return hex.toUpperCase(Locale.ROOT);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double luminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
    }

    public static double ratio(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** The header text colour to use on a coloured header: white or dark, whichever reads better. */
    public static String headerTextOn(String primary) {
        return ratio(WHITE, primary) >= ratio(DARK_HEADER_TEXT, primary) ? WHITE : DARK_HEADER_TEXT;
    }

    /** True when the accent reads as text on the surface and the background and carries header text. */
    public static boolean passes(String accent, String surface, String background) {
        return ratio(accent, surface) >= MIN_TEXT_RATIO
                && ratio(accent, background) >= MIN_TEXT_RATIO
                && ratio(headerTextOn(accent), accent) >= MIN_TEXT_RATIO;
    }

    /**
     * The nearest passing colour: same hue and saturation, lightness moved 1 percent at a time (darker
     * for a light variant, lighter for a dark one). Null when none passes.
     */
    public static String adjust(String accent, boolean darkVariant, String surface, String background) {
        double[] hsl = toHsl(accent);
        for (int step = 0; step <= 100; step++) {
            double l = darkVariant ? hsl[2] + step * 0.01 : hsl[2] - step * 0.01;
            if (l < 0 || l > 1) {
                return null;
            }
            String candidate = toHex(hsl[0], hsl[1], l);
            if (passes(candidate, surface, background)) {
                return candidate;
            }
        }
        return null;
    }

    static double[] toHsl(String hex) {
        double r = Integer.parseInt(hex.substring(1, 3), 16) / 255.0;
        double g = Integer.parseInt(hex.substring(3, 5), 16) / 255.0;
        double b = Integer.parseInt(hex.substring(5, 7), 16) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double d = max - min;
        if (d == 0) {
            return new double[] {0, 0, l};
        }
        double s = d / (1 - Math.abs(2 * l - 1));
        double h;
        if (max == r) {
            h = ((g - b) / d) % 6;
            if (h < 0) {
                h += 6;
            }
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        return new double[] {h * 60, s, l};
    }

    static String toHex(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double[] rgb;
        switch ((int) Math.floor(h / 60) % 6) {
            case 0 -> rgb = new double[] {c, x, 0};
            case 1 -> rgb = new double[] {x, c, 0};
            case 2 -> rgb = new double[] {0, c, x};
            case 3 -> rgb = new double[] {0, x, c};
            case 4 -> rgb = new double[] {x, 0, c};
            default -> rgb = new double[] {c, 0, x};
        }
        return String.format(
                "#%02X%02X%02X",
                Math.round((rgb[0] + m) * 255), Math.round((rgb[1] + m) * 255), Math.round((rgb[2] + m) * 255));
    }
}
