package com.hls.identity.mobile;

/** A dotted numeric app version such as {@code 1.2.0}, comparable by its numeric parts. */
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {

    /** Parses {@code major.minor.patch}; missing or non-numeric parts make the version invalid. */
    public static AppVersion parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("version is required");
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("version must look like 1.2.0: " + value);
        }
        try {
            return new AppVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version must look like 1.2.0: " + value, e);
        }
    }

    @Override
    public int compareTo(AppVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
