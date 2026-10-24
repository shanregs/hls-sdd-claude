package com.hls.schoolbilling.internal;

import com.hls.school.api.InvalidInputException;
import java.util.Locale;
import java.util.Set;

/**
 * The file types a MoU document may have (spec 031): pdf, jpg, jpeg and png. The shared store also allows docx, xlsx and
 * txt, so the extension is checked here first; the store then checks the content and the 10 MB cap.
 */
final class MouDocumentPolicy {

    static final String NOT_ALLOWED = "Only PDF, JPG or PNG files can be uploaded.";
    private static final Set<String> EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private MouDocumentPolicy() {}

    static void check(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidInputException("The file needs a name.");
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidInputException(NOT_ALLOWED);
        }
    }
}
