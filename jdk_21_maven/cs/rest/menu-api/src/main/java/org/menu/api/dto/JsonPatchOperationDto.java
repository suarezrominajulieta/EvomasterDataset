package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One RFC 6902 operation, declared by hand rather than reusing the library's {@code JsonPatch}.
 *
 * <p>That type serialises to an opaque schema, and an opaque schema is useless to a fuzzer: with
 * {@code op} as a free string almost every generated operation is rejected before reaching any
 * interesting code. The allowed values are spelled out here for exactly that reason, and the
 * examples on {@code path} point at fields that really exist.
 */
@Schema(description = "A single JSON Patch (RFC 6902) operation")
public class JsonPatchOperationDto {

    @Schema(allowableValues = {"add", "remove", "replace", "move", "copy", "test"},
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "replace")
    private String op;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            example = "/price",
            description = "JSON Pointer into the entity, e.g. /name, /price, /currency, /color")
    private String path;

    @Schema(description = "The value for add, replace and test. Any JSON type.",
            example = "12.5")
    private Object value;

    @Schema(example = "/name", description = "Source pointer, required by move and copy only")
    private String from;

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
