package com.hls.files.internal;

import com.hls.school.api.InvalidInputException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Which files may be stored: jpg, png, pdf, docx, xlsx and txt. A file must carry an allowed extension and its first
 * bytes must match that type, so a renamed program or page is refused. The content type is decided here, never taken
 * from the client.
 */
final class FileTypePolicy {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("txt", "text/plain"),
            // spec 029: content files, only accepted for callers whose limits list them
            Map.entry("mp4", "video/mp4"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("wav", "audio/wav"));

    /** How many leading bytes {@link #checkHeader} needs. */
    static final int HEADER_BYTES = 16;

    private static final java.util.Set<String> DEFAULT_TYPES = java.util.Set.of("jpg", "jpeg", "png", "pdf", "docx", "xlsx", "txt");

    private FileTypePolicy() {}

    /** The extension in lower case, without the dot, or "" when there is none. */
    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Checks the extension against the caller's limits and the first bytes of a streamed file; returns the content type.
     * Plain text needs the whole content to be checked and is therefore not available to streamed uploads.
     */
    static String checkHeader(String originalName, byte[] header, int length, com.hls.files.api.FileLimits limits) {
        String extension = extensionOf(originalName);
        String type = TYPES.get(extension);
        if (type == null || limits.limitFor(extension) < 0 || "text/plain".equals(type)) {
            throw new InvalidInputException("This file type is not accepted here. Accepted: " + accepted(limits) + ".");
        }
        byte[] head = java.util.Arrays.copyOf(header, length);
        if (!matches(type, extension, head)) {
            throw new InvalidInputException("The file content does not match its ." + extension + " type.");
        }
        return type;
    }

    private static String accepted(com.hls.files.api.FileLimits limits) {
        return limits.maxBytesByExtension().keySet().stream().filter(e -> !e.equals("txt") && !e.equals("jpeg")).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static boolean matches(String type, String extension, byte[] content) {
        return switch (type) {
            case "image/jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "application/pdf" -> startsWith(content, '%', 'P', 'D', 'F', '-');
            case "text/plain" -> looksLikeText(content);
            case "video/mp4", "audio/mp4" -> content.length >= 12 && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p';
            case "audio/mpeg" -> startsWith(content, 'I', 'D', '3') || (content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xE0) == 0xE0);
            case "audio/wav" -> content.length >= 12 && startsWith(content, 'R', 'I', 'F', 'F') && content[8] == 'W' && content[9] == 'A' && content[10] == 'V' && content[11] == 'E';
            default -> startsWith(content, 'P', 'K', 0x03, 0x04); // docx and xlsx are zip packages
        };
    }

    /** Checks the name and the first bytes; returns the content type to store and serve. */
    static String check(String originalName, byte[] content) {
        String extension = extensionOf(originalName);
        String type = TYPES.get(extension);
        if (type == null || !DEFAULT_TYPES.contains(extension)) {
            throw new InvalidInputException("Only jpg, png, pdf, docx, xlsx and txt files can be uploaded.");
        }
        if (!matches(type, extension, content)) {
            throw new InvalidInputException("The file content does not match its ." + extension + " type.");
        }
        return type;
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
