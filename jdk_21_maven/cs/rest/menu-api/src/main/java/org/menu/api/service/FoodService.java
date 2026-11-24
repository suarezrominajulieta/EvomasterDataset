package org.menu.api.service;

import org.menu.api.domain.Category;
import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.FoodDto;
import org.menu.api.dto.FoodPictureDto;
import org.menu.api.mapper.ModelsMapper;
import org.menu.api.repository.CategoryRepository;
import org.menu.api.repository.FoodRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class FoodService {

    /** Guards against a client asking for the whole table in one go. */
    private static final int MAX_COUNT = 200;

    private final FoodRepository repository;
    private final CategoryRepository categoryRepository;
    private final CurrencyProvider currencies;
    private final FileStorageService storage;
    private final FileValidationService validation;
    private final ModelsMapper mapper;

    public FoodService(FoodRepository repository,
                       CategoryRepository categoryRepository,
                       CurrencyProvider currencies,
                       FileStorageService storage,
                       FileValidationService validation,
                       ModelsMapper mapper) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.currencies = currencies;
        this.storage = storage;
        this.validation = validation;
        this.mapper = mapper;
    }

    /** The result of a PUT: the stored document, and whether it actually changed. */
    public record UpdateResult(FoodDto food, boolean modified) {
    }

    @Transactional(readOnly = true)
    public List<FoodDto> findAll(int count, int skip) {
        // The original took these as path segments and let a negative value reach the database,
        // which answered with a driver error the API rendered as a 500. Here they are query
        // parameters, and each rejection is a branch of its own.
        if (count < 0) {
            throw new InvalidRequestException("count cannot be negative");
        }
        if (skip < 0) {
            throw new InvalidRequestException("skip cannot be negative");
        }
        if (count > MAX_COUNT) {
            throw new InvalidRequestException("count cannot be greater than " + MAX_COUNT);
        }
        if (count == 0) {
            return List.of();
        }
        List<Food> page = repository.findAll(Sort.by("name")).stream()
                .skip(skip)
                .limit(count)
                .toList();
        return mapper.toFoodDtos(page);
    }

    @Transactional(readOnly = true)
    public FoodDto findById(UUID id) {
        return mapper.toDto(get(id));
    }

    public Food get(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("food", id));
    }

    public Food save(Food food) {
        return repository.save(food);
    }

    public FoodDto create(FoodDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("a food document is required");
        }
        String problem = businessProblem(dto);
        if (problem != null) {
            throw new InvalidRequestException(problem);
        }
        // On this path an unknown category is part of a malformed request, so it is a 400.
        // Reaching the same state through PATCH is a disagreement with stored data instead:
        // see savePatched below.
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new InvalidRequestException(
                        "no category with id " + dto.getCategoryId()));

        Food food = new Food();
        food.setId(UUID.randomUUID());
        apply(food, dto, category);
        return mapper.toDto(repository.save(food));
    }

    public UpdateResult update(UUID id, FoodDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("a food document is required");
        }
        if (dto.getId() != null && !dto.getId().equals(id)) {
            throw new InvalidRequestException("id in the path and in the body disagree");
        }
        String problem = businessProblem(dto);
        if (problem != null) {
            throw new InvalidRequestException(problem);
        }
        Food food = get(id);
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new InvalidRequestException(
                        "no category with id " + dto.getCategoryId()));

        boolean changed = differs(food, dto, category);
        boolean detached = detachPictures(food, dto.getDeletedPictures());
        if (changed) {
            apply(food, dto, category);
            repository.save(food);
        }
        return new UpdateResult(mapper.toDto(food), changed || detached);
    }

    public void delete(UUID id) {
        Food food = get(id);
        for (FoodPicture picture : food.getPictures()) {
            storage.delete(picture.getFileName());
        }
        storage.delete(food.getAllergenSheetFileName());
        repository.delete(food);
    }

    /**
     * {@code POST /api/v1/Foods/{id}/allergens}: a PDF part whose content type the schema pins
     * to application/pdf. A second upload replaces the first.
     *
     * <p>The checks run in the same order as the PNG icon of a category - dish, then content
     * type, then name, then bytes - so that the two endpoints differ in exactly one thing: whether
     * the signature they want can survive being sent as text. See
     * {@link FileValidationService#validatePdf}.
     */
    public FoodDto setAllergenSheet(UUID id, MultipartFile sheet) {
        Food food = get(id);
        validation.requireContentType(sheet, "sheet", "application/pdf");
        validation.validatePdf(sheet, "sheet");

        String previous = food.getAllergenSheetFileName();
        food.setAllergenSheetFileName(storage.store(sheet));
        storage.delete(previous);
        return mapper.toDto(repository.save(food));
    }

    /**
     * Writes a patched document back. An unknown category is a 409 here rather than the 400 a
     * POST gets: the patch itself was a valid request, it just does not agree with what is stored.
     */
    public FoodDto savePatched(Food food, FoodDto dto) {
        String problem = businessProblem(dto);
        if (problem != null) {
            throw new UnprocessableEntityException(problem);
        }
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ConflictException("no category with id " + dto.getCategoryId()));
        apply(food, dto, category);
        return mapper.toDto(repository.save(food));
    }

    public FoodDto toDto(Food food) {
        return mapper.toDto(food);
    }

    private void apply(Food food, FoodDto dto, Category category) {
        food.setName(dto.getName().trim());
        food.setDescription(dto.getDescription());
        food.setRecept(dto.getRecept());
        food.setPrice(dto.getPrice());
        food.setCurrency(dto.getCurrency());
        food.setCategory(category);
    }

    private boolean differs(Food food, FoodDto dto, Category category) {
        return !Objects.equals(food.getName(), dto.getName().trim())
                || !Objects.equals(food.getDescription(), dto.getDescription())
                || !Objects.equals(food.getRecept(), dto.getRecept())
                || food.getPrice() == null
                || food.getPrice().compareTo(dto.getPrice()) != 0
                || !Objects.equals(food.getCurrency(), dto.getCurrency())
                || !Objects.equals(food.getCategoryId(), category.getId());
    }

    /** Detaches the listed pictures, deleting their stored files. True when any went. */
    private boolean detachPictures(Food food, List<FoodPictureDto> deleted) {
        if (deleted == null || deleted.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (FoodPictureDto wanted : deleted) {
            if (wanted == null || wanted.getId() == null) {
                continue;
            }
            FoodPicture picture = food.getPictures().stream()
                    .filter(p -> p.getId().equals(wanted.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ConflictException(
                            "picture " + wanted.getId() + " is not attached to food " + food.getId()));
            storage.delete(picture.getFileName());
            food.getPictures().remove(picture);
            any = true;
        }
        if (any) {
            repository.save(food);
        }
        return any;
    }

    /** What is wrong with this document, or null when nothing is. */
    private String businessProblem(FoodDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return "name is required and cannot be blank";
        }
        if (dto.getPrice() == null) {
            return "price is required";
        }
        if (dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return "price cannot be negative";
        }
        if (dto.getCurrency() != null && !currencies.isKnown(dto.getCurrency())) {
            return "currency " + dto.getCurrency() + " is not a known ISO code";
        }
        if (dto.getCategoryId() == null) {
            return "categoryId is required";
        }
        return null;
    }
}
