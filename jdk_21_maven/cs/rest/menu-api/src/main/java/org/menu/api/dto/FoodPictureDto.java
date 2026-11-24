package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A picture attached to a dish")
public class FoodPictureDto {

    private UUID id;

    @Schema(description = "Public path of the stored file", example = "/pictures/1.jpg")
    private String filePath;

    public FoodPictureDto() {
    }

    public FoodPictureDto(UUID id, String filePath) {
        this.id = id;
        this.filePath = filePath;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
