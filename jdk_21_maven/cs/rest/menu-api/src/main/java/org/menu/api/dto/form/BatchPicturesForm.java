package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.menu.api.dto.PictureMetadataDto;

/**
 * Body of {@code POST /api/v1/Foods/{id}/pictures/batch}: files next to an object part that
 * travels as {@code application/json}, declared through {@code encoding.metadata.contentType}.
 */
@Schema(description = "Several pictures plus a JSON metadata part")
public class BatchPicturesForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @ArraySchema(schema = @Schema(type = "string", format = "binary"),
            arraySchema = @Schema(description = "One or more image files",
                    requiredMode = Schema.RequiredMode.REQUIRED))
    public Object files;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Sent as its own application/json part")
    public PictureMetadataDto metadata;
}
