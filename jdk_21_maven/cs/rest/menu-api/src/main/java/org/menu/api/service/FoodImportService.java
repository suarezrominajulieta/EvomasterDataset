package org.menu.api.service;

import org.menu.api.domain.Category;
import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.FoodDto;
import org.menu.api.mapper.ModelsMapper;
import org.menu.api.repository.CategoryRepository;
import org.menu.api.repository.FoodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /api/v1/Foods/import}: a text/csv part plus a boolean part.
 *
 * <p>The parser behind it is the deepest branch any single upload reaches, and a dry run walks
 * every one of those branches without storing anything.
 */
@Service
@Transactional
public class FoodImportService {

    private static final Set<String> CSV_EXTENSIONS = Set.of("csv", "txt");

    private final FoodRepository foods;
    private final CategoryRepository categories;
    private final FileValidationService validation;
    private final ModelsMapper mapper;

    public FoodImportService(FoodRepository foods,
                             CategoryRepository categories,
                             FileValidationService validation,
                             ModelsMapper mapper) {
        this.foods = foods;
        this.categories = categories;
        this.validation = validation;
        this.mapper = mapper;
    }

    /** What an import produced, or would have produced. */
    public record ImportResult(int imported, boolean dryRun, List<FoodDto> foods) {
    }

    public ImportResult importFrom(MultipartFile csv, boolean dryRun) {
        validation.validateNonImage(csv, "csv", CSV_EXTENSIONS);
        validation.requireContentType(csv, "csv", "text/csv");

        String content;
        try {
            content = new String(csv.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DishCsvParser.Row> rows = DishCsvParser.parse(content);
        List<Food> parsed = new ArrayList<>();
        for (DishCsvParser.Row row : rows) {
            parsed.add(toFood(row));
        }

        if (dryRun) {
            // Nothing is written, but the answer is the same shape, so the two paths stay
            // comparable from outside.
            return new ImportResult(parsed.size(), true, parsed.stream().map(mapper::toDto).toList());
        }
        List<Food> saved = foods.saveAll(parsed);
        return new ImportResult(saved.size(), false, mapper.toFoodDtos(saved));
    }

    private Food toFood(DishCsvParser.Row row) {
        if (row.name().isBlank()) {
            throw new UnprocessableEntityException("line " + row.lineNumber() + " has no name");
        }
        if (row.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new UnprocessableEntityException(
                    "line " + row.lineNumber() + " has a negative price");
        }
        Category category = categories.findFirstByName(row.categoryName())
                .orElseThrow(() -> new ConflictException(
                        "line " + row.lineNumber() + " names the category " + row.categoryName()
                                + ", which does not exist"));

        Food food = new Food();
        food.setId(UUID.randomUUID());
        food.setName(row.name());
        food.setDescription(row.description());
        food.setPrice(row.price());
        food.setCurrency("USD");
        food.setCategory(category);
        if (!row.dishId().isBlank()) {
            FoodPicture picture = new FoodPicture();
            picture.setId(UUID.randomUUID());
            picture.setFileName(row.dishId() + ".jpg");
            food.addPicture(picture);
        }
        return food;
    }
}
