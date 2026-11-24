package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "What an upload endpoint answers with on success")
public record UploadResultDto(
        @Schema(example = "3") int uploaded,
        @Schema(description = "Total bytes accepted") long totalBytes,
        List<FoodPictureDto> pictures,
        @Schema(description = "Set when the request only validated and stored nothing")
        Boolean dryRun) {

    public UploadResultDto(int uploaded, long totalBytes, List<FoodPictureDto> pictures) {
        this(uploaded, totalBytes, pictures, null);
    }
}
