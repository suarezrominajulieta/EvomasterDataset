package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * What {@code /api/v1/FoodPictures/{id}} answers with, and the document its PATCH operates on.
 *
 * <p>Fuller than the {@link FoodPictureDto} embedded in a dish: everything the upload recorded
 * is here, but only {@code caption} and {@code displayOrder} can be patched. The rest describes
 * the stored file and is the server's to set.
 */
@Schema(description = "A stored picture, in full")
public class FoodPictureDetailDto {

    private UUID id;

    @Schema(example = "/pictures/1.jpg", description = "Read only")
    private String filePath;

    @Schema(description = "The name the client sent. Read only.")
    private String originalFileName;

    @Schema(description = "Size in bytes. Read only.")
    private long length;

    @Schema(example = "image/jpeg", description = "Read only")
    private String contentType;

    @Schema(example = "front view")
    @Size(max = 255, message = "must be at most 255 characters")
    private String caption;

    @Schema(example = "1")
    private Integer displayOrder;

    @Schema(description = "The dish this picture belongs to. Read only.")
    private UUID foodId;

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

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }
}
