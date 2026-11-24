package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /api/v1/Foods/{id}/allergens}: one file part whose content type is pinned
 * to application/pdf by {@code encoding.sheet.contentType}.
 *
 * <p>The same shape as the category icon, on purpose. The icon wants a PNG, whose signature
 * starts with a byte a UTF-8 string cannot carry; this wants a PDF, whose signature is plain
 * ASCII. Everything else about the two requests is alike, so a client that gets one through and
 * never the other is telling you how it encodes binary parts.
 */
@Schema(description = "The allergen sheet of a dish, as a PDF")
public class AllergenSheetForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "A PDF: starts with %PDF- and ends with a %%EOF trailer")
    public Object sheet;
}
