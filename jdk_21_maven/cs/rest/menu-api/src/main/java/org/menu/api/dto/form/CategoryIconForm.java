package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /api/v1/Categories/{id}/icon}: a file part whose content type is pinned
 * to image/png by {@code encoding.icon.contentType}, next to a plain text part.
 */
@Schema(description = "A category icon and the colour to file it under")
public class CategoryIconForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The icon. Has to arrive as image/png.")
    public Object icon;

    @Schema(type = "string", description = "Colour name stored on the category", example = "White")
    public String color;
}
