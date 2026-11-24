package org.menu.api.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /api/v1/Foods/import}: a text/csv file part next to a boolean part.
 */
@Schema(description = "A CSV of dishes, and whether to only validate it")
public class FoodsImportForm {

    // The fields are public because these classes exist only to be introspected: the schema
    // generator reads them through Jackson, which by default only sees public members, and
    // nothing ever constructs one of these.

    @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "CSV with the header DishID,Type,Name,Description,Price")
    public Object csv;

    @Schema(type = "boolean", defaultValue = "false",
            description = "Parse and validate without storing anything", example = "false")
    public Boolean dryRun;
}
