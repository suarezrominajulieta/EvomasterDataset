package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "A dish on the menu")
public class FoodDto {

    @Schema(description = "Server assigned. Ignored on POST, and immutable afterwards.")
    private UUID id;

    @Schema(example = "House Salad")
    @Size(max = 255, message = "must be at most 255 characters")
    private String name;

    @Size(max = 2000, message = "must be at most 2000 characters")
    private String description;

    @Schema(description = "The recipe, spelled the way the .NET original spelled it")
    @Size(max = 4000, message = "must be at most 4000 characters")
    private String recept;

    @Schema(description = "Read only; pictures are attached through the upload endpoints")
    private List<FoodPictureDto> pictures;

    @Schema(example = "9.99")
    private BigDecimal price;

    @Schema(description = "Read only; derived from categoryId")
    private CategoryDto category;

    private UUID categoryId;

    @Schema(example = "USD", description = "ISO code, has to be one listed by /api/v1/Currency")
    private String currency;

    @Schema(description = "Pictures to detach, honoured on PUT only")
    private List<FoodPictureDto> deletedPictures;

    @Schema(description = "Public path of the allergen sheet. Read only; set by the PDF upload.",
            example = "/pictures/1700000000000_...-allergens.pdf")
    private String allergenSheetPath;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRecept() {
        return recept;
    }

    public void setRecept(String recept) {
        this.recept = recept;
    }

    public List<FoodPictureDto> getPictures() {
        return pictures;
    }

    public void setPictures(List<FoodPictureDto> pictures) {
        this.pictures = pictures;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public CategoryDto getCategory() {
        return category;
    }

    public void setCategory(CategoryDto category) {
        this.category = category;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<FoodPictureDto> getDeletedPictures() {
        return deletedPictures;
    }

    public void setDeletedPictures(List<FoodPictureDto> deletedPictures) {
        this.deletedPictures = deletedPictures;
    }

    public String getAllergenSheetPath() {
        return allergenSheetPath;
    }

    public void setAllergenSheetPath(String allergenSheetPath) {
        this.allergenSheetPath = allergenSheetPath;
    }
}
