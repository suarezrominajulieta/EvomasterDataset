package org.menu.api.service;

import org.menu.api.domain.Category;
import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.repository.CategoryRepository;
import org.menu.api.repository.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fills an empty database from {@code src/main/resources/setup}, so that a fuzzer arriving at a
 * freshly started instance finds something to read, delete and patch.
 *
 * <p>Idempotent: each table is only touched when it is empty. The pictures the seed names are
 * rows only; the twenty jpegs the .NET original shipped are not carried here, so
 * {@code /pictures/1.jpg} names a file that was never written. Nothing in this API serves those
 * bytes back, so nothing depends on them existing.
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final CategoryRepository categories;
    private final FoodRepository foods;

    public SeedRunner(CategoryRepository categories, FoodRepository foods) {
        this.categories = categories;
        this.foods = foods;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (categories.count() == 0) {
            seedCategories();
        }
        if (foods.count() == 0) {
            seedFoods();
        }
    }

    private void seedCategories() throws IOException {
        List<Category> created = new ArrayList<>();
        for (String name : read("setup/categories.csv").split(",")) {
            if (name.isBlank()) {
                continue;
            }
            Category category = new Category();
            category.setId(fixedId("category-" + name.trim()));
            category.setName(name.trim());
            category.setColor("White");
            created.add(category);
        }
        categories.saveAll(created);
        log.info("seeded {} categories", created.size());
    }

    private void seedFoods() throws IOException {
        List<Food> created = new ArrayList<>();
        for (DishCsvParser.Row row : DishCsvParser.parse(read("setup/dishes.csv"))) {
            Category category = categories.findFirstByName(row.categoryName()).orElse(null);
            if (category == null) {
                log.warn("dish {} names the unknown category {}, skipping", row.dishId(),
                        row.categoryName());
                continue;
            }
            Food food = new Food();
            food.setId(fixedId("food-" + row.dishId()));
            food.setName(row.name());
            food.setDescription(row.description());
            food.setPrice(row.price());
            food.setCurrency("USD");
            food.setCategory(category);

            FoodPicture picture = new FoodPicture();
            picture.setId(fixedId("picture-" + row.dishId()));
            picture.setFileName(row.dishId() + ".jpg");
            food.addPicture(picture);

            created.add(food);
        }
        foods.saveAll(created);
        log.info("seeded {} dishes", created.size());
    }

    //same ids on every start, so generated tests can be replayed against a fresh instance
    private static UUID fixedId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(String path) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
