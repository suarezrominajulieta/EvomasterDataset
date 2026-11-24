package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /api/v1/Foods/UploadFoodImage}: the shape the .NET original had.
 *
 * <p>These form classes exist only to be named from {@code @RequestBody(schema = ...)}. Nothing
 * binds to them: springdoc will not emit a multipart schema, let alone an {@code encoding} block,
 * for bare {@code @RequestPart} parameters, and that schema is the whole point of this project.
 */
@Schema(description = "Several pictures plus the dish they belong to")
public class UploadFoodImageForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @ArraySchema(schema = @Schema(type = "string", format = "binary",
            description = "An image file. Each part carries its own filename."),
            arraySchema = @Schema(description = "One or more image files",
                    requiredMode = Schema.RequiredMode.REQUIRED))
    public Object files;

    @Schema(type = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Id of the dish the pictures belong to",
            example = "00000000-0000-0000-0000-000000000000")
    public String foodId;
}
