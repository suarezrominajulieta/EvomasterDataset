package org.menu.api.service;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies RFC 6902 patches to a DTO, with a different status code for each way a patch can fail.
 *
 * <p>The split that matters: a patch that is <em>malformed</em> is a 400, a patch that is well
 * formed but disagrees with the document it was handed is a 409, and a patch that reaches for a
 * field this API will not let anyone move is a 403. Whatever survives all three is then checked
 * against the business rules by the calling service, which answers 422 or 409 in turn.
 */
@Service
public class JsonPatchService {

    private static final Set<String> OPERATIONS =
            Set.of("add", "remove", "replace", "move", "copy", "test");

    /** Operations that name a source document location as well as a target. */
    private static final Set<String> NEEDS_FROM = Set.of("move", "copy");

    /** Operations that carry a value. */
    private static final Set<String> NEEDS_VALUE = Set.of("add", "replace", "test");

    /** Operations whose target has to already exist. */
    private static final Set<String> NEEDS_EXISTING_TARGET = Set.of("remove", "replace", "test");

    /**
     * A JSON Pointer as RFC 6901 spells it: a sequence of slash separated tokens in which the
     * only legal uses of {@code ~} are {@code ~0} and {@code ~1}.
     */
    private static final Pattern JSON_POINTER = Pattern.compile("(/([^/~]|~[01])*)+");

    private final ObjectMapper mapper;

    public JsonPatchService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Patches {@code current} and answers with the result.
     *
     * @param patch     the request body, exactly as it arrived
     * @param current   the document as it stands
     * @param type      what the patched document has to deserialise back into
     * @param immutable top level field names no operation may touch, in or out
     */
    public <T> T apply(JsonNode patch, T current, Class<T> type, Set<String> immutable) {
        validateShape(patch, immutable);

        JsonNode document = mapper.valueToTree(current);
        checkAgainstDocument(patch, document);

        JsonNode patched;
        try {
            patched = JsonPatch.fromJson(patch).apply(document);
        } catch (JsonPatchException e) {
            // Everything reachable here is the patch disagreeing with the document: a failed
            // test, or a pointer whose parent is not there.
            throw new PatchFailedException("the patch does not apply to this document: "
                    + e.getMessage());
        } catch (java.io.IOException e) {
            throw new InvalidRequestException("the patch could not be read: " + e.getMessage());
        }

        try {
            return mapper.treeToValue(patched, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidRequestException(
                    "the patched document no longer has the expected shape: " + typeProblem(e));
        }
    }

    /** Everything that can be decided by looking at the patch alone. All 400s, plus the 403s. */
    private void validateShape(JsonNode patch, Set<String> immutable) {
        if (patch == null || !patch.isArray()) {
            throw new InvalidRequestException("a JSON Patch body has to be an array of operations");
        }
        if (patch.isEmpty()) {
            throw new InvalidRequestException("a JSON Patch body has to hold at least one operation");
        }
        for (JsonNode operation : patch) {
            if (!operation.isObject()) {
                throw new InvalidRequestException("every patch operation has to be an object");
            }
            JsonNode opNode = operation.get("op");
            if (opNode == null || !opNode.isTextual()) {
                throw new InvalidRequestException("every patch operation needs a textual op");
            }
            String op = opNode.asText();
            if (!OPERATIONS.contains(op)) {
                throw new InvalidRequestException(
                        "unknown op " + op + "; expected one of " + OPERATIONS);
            }
            String path = requirePointer(operation, "path", op);
            if (NEEDS_FROM.contains(op)) {
                String from = requirePointer(operation, "from", op);
                rejectImmutable(from, immutable, op);
            }
            if (NEEDS_VALUE.contains(op) && !operation.has("value")) {
                throw new InvalidRequestException(op + " needs a value");
            }
            rejectImmutable(path, immutable, op);
        }
    }

    /** Everything that needs the stored document in hand. All 409s. */
    private void checkAgainstDocument(JsonNode patch, JsonNode document) {
        for (JsonNode operation : patch) {
            String op = operation.get("op").asText();
            if (NEEDS_EXISTING_TARGET.contains(op)) {
                requireExists(document, operation.get("path").asText(), op);
            }
            if (NEEDS_FROM.contains(op)) {
                requireExists(document, operation.get("from").asText(), op);
            }
        }
    }

    private void requireExists(JsonNode document, String pointer, String op) {
        if (document.at(JsonPointer.compile(pointer)).isMissingNode()) {
            throw new PatchFailedException(
                    op + " names " + pointer + ", which this document does not have");
        }
    }

    private String requirePointer(JsonNode operation, String field, String op) {
        JsonNode node = operation.get(field);
        if (node == null || !node.isTextual()) {
            throw new InvalidRequestException(op + " needs a textual " + field);
        }
        String pointer = node.asText();
        if (!JSON_POINTER.matcher(pointer).matches()) {
            throw new InvalidRequestException(
                    field + " " + pointer + " is not a valid JSON Pointer; it has to start with /"
                            + " and may only escape as ~0 or ~1");
        }
        return pointer;
    }

    /**
     * Some fields are the server's to set. Reaching for one is neither a malformed request nor a
     * disagreement about state, so it gets its own 403 rather than being folded into either.
     */
    private void rejectImmutable(String pointer, Set<String> immutable, String op) {
        String first = firstToken(pointer);
        if (immutable.contains(first)) {
            throw new ForbiddenFieldException(
                    op + " may not touch /" + first + ", which is not yours to change");
        }
    }

    /** The first token of a pointer, unescaped. */
    private static String firstToken(String pointer) {
        String rest = pointer.substring(1);
        int slash = rest.indexOf('/');
        String token = slash < 0 ? rest : rest.substring(0, slash);
        return token.replace("~1", "/").replace("~0", "~");
    }

    /** The field Jackson tripped over, so the message does not name internal classes. */
    private static String typeProblem(com.fasterxml.jackson.core.JsonProcessingException e) {
        if (!(e instanceof com.fasterxml.jackson.databind.JsonMappingException mapping)) {
            return "a field does not carry a value of the expected type";
        }
        StringBuilder path = new StringBuilder();
        for (var reference : mapping.getPath()) {
            if (reference.getFieldName() == null) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(reference.getFieldName());
        }
        return path.isEmpty()
                ? "a field does not carry a value of the expected type"
                : path + " does not carry a value of the expected type";
    }
}
