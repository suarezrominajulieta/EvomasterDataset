package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/Foods/{id}/picture}: one file part next to two primitive parts. */
@Schema(description = "One picture, with optional caption and ordering")
public class SinglePictureForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The image file")
    public Object file;

    @Schema(type = "string", description = "Free text shown under the picture", example = "front view")
    public String caption;

    @Schema(type = "integer", format = "int32", description = "Position in the gallery", example = "1")
    public Integer displayOrder;
}
