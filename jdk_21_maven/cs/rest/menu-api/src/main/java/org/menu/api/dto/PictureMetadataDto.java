package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The JSON part of the batch upload. It is a part in its own right, sent with
 * {@code Content-Type: application/json} inside the multipart body.
 */
@Schema(description = "Metadata travelling as a JSON part alongside the files")
public class PictureMetadataDto {

    @ArraySchema(schema = @Schema(type = "string", example = "front"),
            arraySchema = @Schema(description = "One caption per uploaded file, in order"))
    private List<String> captions;

    @Schema(description = "Detach the dish's existing pictures before attaching these",
            example = "false")
    private Boolean replaceExisting;

    public List<String> getCaptions() {
        return captions;
    }

    public void setCaptions(List<String> captions) {
        this.captions = captions;
    }

    public Boolean getReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(Boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }
}
