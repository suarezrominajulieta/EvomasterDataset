package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code PUT /api/v1/FoodPictures/{id}/content}. A single file part on a PUT, which is
 * there to catch a client that only ever emits multipart bodies on POST.
 */
@Schema(description = "The replacement image")
public class PictureContentForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The new image file")
    public Object file;
}
