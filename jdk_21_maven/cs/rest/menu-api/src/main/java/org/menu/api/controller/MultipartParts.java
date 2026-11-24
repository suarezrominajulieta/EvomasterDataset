package org.menu.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.menu.api.service.InvalidRequestException;
import org.menu.api.service.UnsupportedFileException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;

/**
 * Reads parts out of a multipart request by hand.
 *
 * <p>Spring can bind them to method parameters, but not in a way that keeps the distinction this
 * API is built to expose: a part only counts as a file when its {@code Content-Disposition}
 * carries a {@code filename}, and a part that arrives without one shows up as an ordinary form
 * field instead. Bound as a parameter that difference turns into whichever error the resolver
 * happens to raise; read here it is a branch with a message of its own.
 */
final class MultipartParts {

    private MultipartParts() {
    }

    /** The part as a file, or a 400 saying which of the two ways it was missing. */
    static MultipartFile requireFile(MultipartHttpServletRequest request, String name) {
        MultipartFile file = request.getFile(name);
        if (file != null) {
            return file;
        }
        if (request.getParameter(name) != null) {
            throw new InvalidRequestException(
                    "part " + name + " arrived without a filename, so it is a form field "
                            + "rather than a file");
        }
        throw new InvalidRequestException("part " + name + " is required");
    }

    /** Every file sent under this name. Empty when none were. */
    static List<MultipartFile> files(MultipartHttpServletRequest request, String name) {
        List<MultipartFile> files = request.getFiles(name);
        if (files.isEmpty() && request.getParameter(name) != null) {
            throw new InvalidRequestException(
                    "part " + name + " arrived without a filename, so it is a form field "
                            + "rather than a file");
        }
        return files;
    }

    /** An optional text part. Null when it was not sent. */
    static String text(MultipartHttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    static String requireText(MultipartHttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            if (request.getFile(name) != null) {
                throw new InvalidRequestException(
                        "part " + name + " carries a filename, so it was read as a file rather "
                                + "than as a text field");
            }
            throw new InvalidRequestException("part " + name + " is required");
        }
        return value;
    }

    static Integer integer(MultipartHttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("part " + name + " is not an integer: " + raw);
        }
    }

    /** A boolean part. Anything other than true or false is a 400, not a silent false. */
    static boolean bool(MultipartHttpServletRequest request, String name, boolean fallback) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new InvalidRequestException("part " + name + " is not a boolean: " + raw);
    }

    /**
     * An object part, sent as its own {@code application/json} section of the body.
     *
     * <p>Whether that content type is there at all is the thing worth checking: a client that
     * ignores the {@code encoding} block of the schema will send this part as plain text, and
     * that is a 415 rather than a parse failure.
     */
    static <T> T json(MultipartHttpServletRequest request,
                      String name,
                      Class<T> type,
                      ObjectMapper mapper) {
        String declared = request.getMultipartContentType(name);
        String raw = request.getParameter(name);
        MultipartFile asFile = request.getFile(name);

        if (raw == null && asFile == null) {
            throw new InvalidRequestException("part " + name + " is required");
        }
        if (declared == null || !baseType(declared).endsWith("json")) {
            throw new UnsupportedFileException(
                    "part " + name + " has to arrive as application/json, not " + declared);
        }
        String body = raw;
        if (body == null) {
            try {
                body = new String(asFile.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("part " + name + " is not readable JSON");
        }
    }

    private static String baseType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim()
                .toLowerCase(java.util.Locale.ROOT);
    }
}
