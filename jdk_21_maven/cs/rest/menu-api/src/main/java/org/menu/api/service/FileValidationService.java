package org.menu.api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every rule an uploaded part has to satisfy, and a distinct status code for each way of
 * breaking one.
 *
 * <p>The codes are not interchangeable and the duplication between them is deliberate: each
 * rejection is a branch a fuzzer either reaches or does not, and collapsing them all into one
 * 400 would make the whole set indistinguishable from outside. A client that never sends a
 * {@code filename}, or always sends plausible bytes, will simply not get here.
 */
@Service
public class FileValidationService {

    /** Well under the container limit, so that a refusal is this code and not the container. */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_FILES_PER_REQUEST = 10;
    public static final int MAX_FILENAME_LENGTH = 255;

    private static final byte[] PDF_TRAILER =
            "%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** Real PDFs often end in a newline or two after the trailer, so look a little way back. */
    private static final int PDF_TRAILER_WINDOW = 1024;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp");

    /**
     * Checks one part, in an order chosen so that every rule below stays reachable: what the
     * name says comes before what the bytes say.
     */
    public void validate(MultipartFile file, String partName) {
        String originalName = checkName(file, partName);

        String extension = extensionOf(originalName);
        if (extension == null) {
            throw new UnsupportedFileException("filename has no extension");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileException(
                    "." + extension + " is not one of the accepted image extensions "
                            + ALLOWED_EXTENSIONS);
        }
        if (hasDoubleExtension(originalName)) {
            throw new UnsupportedFileException(
                    "filename carries more than one extension, which is not accepted");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(baseType(contentType))) {
            throw new UnsupportedFileException(
                    "content type " + contentType + " is not an accepted image type");
        }

        if (file.getSize() == 0) {
            throw new InvalidRequestException("part " + partName + " is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new PayloadTooLargeException(
                    "file is larger than the " + MAX_FILE_BYTES + " byte limit");
        }

        if (!magicBytesMatch(file, extension)) {
            throw new UnprocessableEntityException(
                    "the file contents are not a " + extension + " image");
        }
    }

    /** Checks a whole request: how many files, and whether any name is repeated. */
    public void validateAll(List<MultipartFile> files, String partName) {
        if (files == null || files.isEmpty()) {
            throw new InvalidRequestException("at least one file is required in part " + partName);
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new PayloadTooLargeException(
                    "a request may carry at most " + MAX_FILES_PER_REQUEST + " files");
        }
        Set<String> seen = new HashSet<>();
        for (MultipartFile file : files) {
            validate(file, partName);
            String name = file.getOriginalFilename();
            if (name != null && !seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new DuplicateException("filename " + name + " appears twice in this request");
            }
        }
    }

    /**
     * A non-image part, such as the CSV of the import endpoint. Only the rules that are about
     * the name and the size apply; what the bytes have to look like is the caller's business.
     */
    public void validateNonImage(MultipartFile file, String partName, Set<String> extensions) {
        String originalName = checkName(file, partName);
        String extension = extensionOf(originalName);
        if (extension == null || !extensions.contains(extension)) {
            throw new UnsupportedFileException(
                    "part " + partName + " has to be one of " + extensions);
        }
        if (file.getSize() == 0) {
            throw new InvalidRequestException("part " + partName + " is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new PayloadTooLargeException(
                    "file is larger than the " + MAX_FILE_BYTES + " byte limit");
        }
    }

    /**
     * A PDF part: the name rules every upload gets, then what the bytes have to look like.
     *
     * <p>Both byte checks are ASCII on purpose. {@code %PDF-} at the start and {@code %%EOF} near
     * the end survive being carried through a UTF-8 {@code String}, where a PNG's {@code 0x89}
     * or a JPEG's {@code 0xFF} do not. So this endpoint and the PNG icon endpoint make a pair:
     * the same shape of request, one signature that survives text encoding and one that cannot.
     * A client that clears this one and never the other is sending binary parts as text.
     */
    public void validatePdf(MultipartFile file, String partName) {
        String originalName = checkName(file, partName);

        String extension = extensionOf(originalName);
        if (!"pdf".equals(extension)) {
            throw new UnsupportedFileException("part " + partName + " has to be a .pdf file");
        }
        if (hasDoubleExtension(originalName)) {
            throw new UnsupportedFileException(
                    "filename carries more than one extension, which is not accepted");
        }
        if (file.getSize() == 0) {
            throw new InvalidRequestException("part " + partName + " is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new PayloadTooLargeException(
                    "file is larger than the " + MAX_FILE_BYTES + " byte limit");
        }
        if (!startsWith(head(file, 5), 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            throw new UnprocessableEntityException("the file does not start with a PDF header");
        }
        // The deeper branch: anything can open with five right bytes, but only a document that
        // was written through to the end carries the trailer.
        if (!tailContains(file, PDF_TRAILER, PDF_TRAILER_WINDOW)) {
            throw new UnprocessableEntityException(
                    "the file has a PDF header but no %%EOF trailer, so it is not a whole document");
        }
    }

    /** The content type the part itself declared, for endpoints that pin it. */
    public void requireContentType(MultipartFile file, String partName, String expected) {
        String actual = file.getContentType();
        if (actual == null || !baseType(actual).equalsIgnoreCase(expected)) {
            throw new UnsupportedFileException(
                    "part " + partName + " has to arrive as " + expected + ", not " + actual);
        }
    }

    /**
     * The rules about the name that every upload shares, whatever it is meant to contain.
     * Answers with the name, now known to be safe to look at further.
     */
    private String checkName(MultipartFile file, String partName) {
        requirePresent(file, partName);

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            // Spring only treats a part as a file when Content-Disposition carries a filename.
            // Without one the part arrives as an ordinary form field, and this is where that
            // shows up.
            throw new InvalidRequestException(
                    "part " + partName + " arrived without a filename, so it is a form field "
                            + "rather than a file");
        }
        if (originalName.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidRequestException(
                    "filename is longer than " + MAX_FILENAME_LENGTH + " characters");
        }
        if (hasControlCharacters(originalName)) {
            throw new InvalidRequestException("filename contains a control character or a null byte");
        }
        if (looksLikeTraversal(originalName)) {
            // Refused here, and nothing downstream builds a path out of it either: the bytes
            // go into a row under a generated name. See FileStorageService.
            throw new ForbiddenFieldException("filename may not contain a path");
        }
        return originalName;
    }

    private void requirePresent(MultipartFile file, String partName) {
        if (file == null) {
            throw new InvalidRequestException("part " + partName + " is required");
        }
    }

    private static boolean hasControlCharacters(String name) {
        return name.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
    }

    /**
     * Anything that reads as a path rather than a bare name, however it is spelled. Nothing is
     * written to a filesystem any more, so this is about refusing the shape of the attack and
     * keeping the branch a client can reach, not about guarding a directory.
     */
    private static boolean looksLikeTraversal(String name) {
        return name.contains("/")
                || name.contains("\\")
                || name.contains("..")
                || name.matches("(?i)^[a-z]:.*");
    }

    /** Lower case extension without the dot, or null when there is none. */
    public static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True for names like foo.png.jsp, where the real extension is not the visible one. */
    private static boolean hasDoubleExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return false;
        }
        String stem = name.substring(0, lastDot);
        String inner = extensionOf(stem);
        // A second dot only counts when what precedes it reads as an extension too. That keeps
        // ordinary names such as "creme.brulee.jpg" out of it while catching "shell.png.jsp".
        return inner != null && inner.length() <= 5 && inner.chars().allMatch(Character::isLetterOrDigit);
    }

    private static String baseType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the first bytes are what the extension promises.
     *
     * <p>This is the rule that a client only clears by sending real image bytes rather than
     * arbitrary text, which is exactly what makes it worth having here.
     */
    private boolean magicBytesMatch(MultipartFile file, String extension) {
        byte[] head = head(file, 12);
        return switch (extension) {
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47);
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(head, 0x47, 0x49, 0x46, 0x38);
            case "webp" -> startsWith(head, 0x52, 0x49, 0x46, 0x46)
                    && head.length >= 12
                    && (head[8] & 0xFF) == 0x57 && (head[9] & 0xFF) == 0x45
                    && (head[10] & 0xFF) == 0x42 && (head[11] & 0xFF) == 0x50;
            default -> false;
        };
    }

    private static byte[] head(MultipartFile file, int count) {
        try (var stream = file.getInputStream()) {
            return stream.readNBytes(count);
        } catch (IOException e) {
            // Reading back a part that the container already buffered is not something a client
            // can provoke, so this is a genuine fault and travels as one.
            throw new UncheckedIOException(e);
        }
    }

    /** Whether {@code needle} appears within the last {@code window} bytes of the part. */
    private static boolean tailContains(MultipartFile file, byte[] needle, int window) {
        byte[] all;
        try {
            all = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int from = Math.max(0, all.length - window);
        outer:
        for (int i = all.length - needle.length; i >= from; i--) {
            for (int j = 0; j < needle.length; j++) {
                if (all[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
