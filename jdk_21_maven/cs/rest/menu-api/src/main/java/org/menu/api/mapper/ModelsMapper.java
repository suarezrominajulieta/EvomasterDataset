package org.menu.api.mapper;

import org.menu.api.domain.Category;
import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.CategoryDto;
import org.menu.api.dto.FoodDto;
import org.menu.api.dto.FoodPictureDetailDto;
import org.menu.api.dto.FoodPictureDto;
import org.springframework.stereotype.Component;

import java.util.List;

/** Entities to DTOs and back. Hand written; there is not enough here to justify a mapping library. */
@Component
public class ModelsMapper {

    /** Public prefix under which stored pictures are addressed. */
    public static final String PICTURES_PREFIX = "/pictures/";

    public CategoryDto toDto(Category category) {
        if (category == null) {
            return null;
        }
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setColor(category.getColor());
        dto.setIconPath(category.getIconFileName() == null
                ? null
                : PICTURES_PREFIX + category.getIconFileName());
        return dto;
    }

    public List<CategoryDto> toCategoryDtos(List<Category> categories) {
        return categories.stream().map(this::toDto).toList();
    }

    public FoodPictureDto toDto(FoodPicture picture) {
        if (picture == null) {
            return null;
        }
        return new FoodPictureDto(picture.getId(), PICTURES_PREFIX + picture.getFileName());
    }

    public FoodPictureDetailDto toDetailDto(FoodPicture picture) {
        if (picture == null) {
            return null;
        }
        FoodPictureDetailDto dto = new FoodPictureDetailDto();
        dto.setId(picture.getId());
        dto.setFilePath(PICTURES_PREFIX + picture.getFileName());
        dto.setOriginalFileName(picture.getOriginalFileName());
        dto.setLength(picture.getLength());
        dto.setContentType(picture.getContentType());
        dto.setCaption(picture.getCaption());
        dto.setDisplayOrder(picture.getDisplayOrder());
        dto.setFoodId(picture.getFoodId());
        return dto;
    }

    public FoodDto toDto(Food food) {
        if (food == null) {
            return null;
        }
        FoodDto dto = new FoodDto();
        dto.setId(food.getId());
        dto.setName(food.getName());
        dto.setDescription(food.getDescription());
        dto.setRecept(food.getRecept());
        dto.setPrice(food.getPrice());
        dto.setCurrency(food.getCurrency());
        dto.setCategory(toDto(food.getCategory()));
        dto.setCategoryId(food.getCategoryId());
        dto.setPictures(food.getPictures().stream().map(this::toDto).toList());
        dto.setAllergenSheetPath(food.getAllergenSheetFileName() == null
                ? null
                : PICTURES_PREFIX + food.getAllergenSheetFileName());
        return dto;
    }

    public List<FoodDto> toFoodDtos(List<Food> foods) {
        return foods.stream().map(this::toDto).toList();
    }

    /**
     * The stored file name behind a {@code filePath}.
     *
     * <p>The .NET original mapped this direction by storing the whole {@code filePath} as the
     * file name, so a round trip kept prefixing itself. Here the prefix is stripped back off.
     */
    public String toFileName(String filePath) {
        if (filePath == null) {
            return null;
        }
        return filePath.startsWith(PICTURES_PREFIX)
                ? filePath.substring(PICTURES_PREFIX.length())
                : filePath;
    }
}
