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

    private static final Map<String, String> TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "txt", "text/plain");

    private FileTypePolicy() {}

    /** The extension in lower case, without the dot, or "" when there is none. */
    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Checks the name and the first bytes; returns the content type to store and serve. */
    static String check(String originalName, byte[] content) {
        String extension = extensionOf(originalName);
        String type = TYPES.get(extension);
        if (type == null) {
            throw new InvalidInputException("Only jpg, png, pdf, docx, xlsx and txt files can be uploaded.");
        }
        boolean matches =
                switch (type) {
                    case "image/jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
                    case "image/png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                    case "application/pdf" -> startsWith(content, '%', 'P', 'D', 'F', '-');
                    case "text/plain" -> looksLikeText(content);
                    default -> startsWith(content, 'P', 'K', 0x03, 0x04); // docx and xlsx are zip packages
                };
        if (!matches) {
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
