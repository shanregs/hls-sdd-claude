package com.hls.files.api;

import java.util.Locale;
import java.util.Map;

/**
 * The file types an owner accepts and the largest size of each (spec 029). A caller that passes its own table can take
 * bigger files of some types than the shared default; every other caller keeps {@link #defaults()}.
 *
 * @param maxBytesByExtension lower-case extension without the dot to the largest size in bytes
 */
public record FileLimits(Map<String, Long> maxBytesByExtension) {

    private static final long MB = 1024L * 1024;

    private static final FileLimits DEFAULTS = new FileLimits(Map.of(
            "jpg", 10 * MB,
            "jpeg", 10 * MB,
            "png", 10 * MB,
            "pdf", 10 * MB,
            "docx", 10 * MB,
            "xlsx", 10 * MB,
            "txt", 10 * MB));

    /** The behaviour of every caller before spec 029: jpg, png, pdf, docx, xlsx and txt, at most 10 MB. */
    public static FileLimits defaults() {
        return DEFAULTS;
    }

    /** The largest size in bytes for the extension, or -1 when the type is not accepted. */
    public long limitFor(String extension) {
        if (extension == null) {
            return -1;
        }
        return maxBytesByExtension.getOrDefault(extension.toLowerCase(Locale.ROOT), -1L);
    }
}
