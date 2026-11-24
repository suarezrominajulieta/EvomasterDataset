package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "A menu category, such as Salads or Desert")
public class CategoryDto {

    @Schema(description = "Server assigned. Ignored on POST, and immutable afterwards.")
    private UUID id;

    @Schema(example = "Salads")
    @Size(max = 255, message = "must be at most 255 characters")
    private String name;

    @Schema(example = "White")
    @Size(max = 255, message = "must be at most 255 characters")
    private String color;

    @Schema(description = "Public path of the uploaded icon. Read only; set by the icon upload.",
            example = "/pictures/1700000000000_...-icon.png")
    private String iconPath;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
