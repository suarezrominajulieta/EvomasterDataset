package org.menu.api.service;

import org.menu.api.domain.Category;
import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.CategoryDto;
import org.menu.api.mapper.ModelsMapper;
import org.menu.api.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository repository;
    private final FileStorageService storage;
    private final FileValidationService validation;
    private final ModelsMapper mapper;

    public CategoryService(CategoryRepository repository,
                           FileStorageService storage,
                           FileValidationService validation,
                           ModelsMapper mapper) {
        this.repository = repository;
        this.storage = storage;
        this.validation = validation;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findAll() {
        return mapper.toCategoryDtos(repository.findAll());
    }

    @Transactional(readOnly = true)
    public CategoryDto findById(UUID id) {
        return mapper.toDto(get(id));
    }

    public Category get(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("category", id));
    }

    public CategoryDto create(CategoryDto dto) {
        rejectIfInvalid(dto);
        Category category = new Category();
        category.setId(UUID.randomUUID());
        apply(category, dto);
        return mapper.toDto(repository.save(category));
    }

    public CategoryDto update(UUID id, CategoryDto dto) {
        if (dto != null && dto.getId() != null && !dto.getId().equals(id)) {
            throw new InvalidRequestException("id in the path and in the body disagree");
        }
        rejectIfInvalid(dto);
        Category category = get(id);
        apply(category, dto);
        return mapper.toDto(repository.save(category));
    }

    /** Takes the category's dishes, and their pictures, with it. */
    public void delete(UUID id) {
        Category category = get(id);
        for (Food food : category.getFoods()) {
            for (FoodPicture picture : food.getPictures()) {
                storage.delete(picture.getFileName());
            }
            storage.delete(food.getAllergenSheetFileName());
        }
        storage.delete(category.getIconFileName());
        repository.delete(category);
    }

    /**
     * {@code POST /api/v1/Categories/{id}/icon}: a file part whose content type the schema pins
     * to image/png, next to a plain text part.
     *
     * <p>The content type is checked before anything else about the part, so that a client which
     * ignored the encoding block of the schema is told exactly that, rather than being told its
     * bytes were wrong.
     */
    public CategoryDto setIcon(UUID id, MultipartFile icon, String color) {
        Category category = get(id);
        validation.requireContentType(icon, "icon", "image/png");
        validation.validate(icon, "icon");

        String previous = category.getIconFileName();
        category.setIconFileName(storage.store(icon));
        if (color != null && !color.isBlank()) {
            category.setColor(color.trim());
        }
        storage.delete(previous);
        return mapper.toDto(repository.save(category));
    }

    /**
     * Writes a patched document back. The same rules as {@link #update}, but a violation here is
     * a 422 rather than a 400: the request itself was fine, the document it produced is not.
     */
    public CategoryDto savePatched(Category category, CategoryDto dto) {
        String problem = businessProblem(dto);
        if (problem != null) {
            throw new UnprocessableEntityException(problem);
        }
        apply(category, dto);
        return mapper.toDto(repository.save(category));
    }

    public CategoryDto toDto(Category category) {
        return mapper.toDto(category);
    }

    private void apply(Category category, CategoryDto dto) {
        category.setName(dto.getName().trim());
        category.setColor(dto.getColor());
    }

    private void rejectIfInvalid(CategoryDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("a category document is required");
        }
        String problem = businessProblem(dto);
        if (problem != null) {
            throw new InvalidRequestException(problem);
        }
    }

    /** What is wrong with this document, or null when nothing is. */
    private static String businessProblem(CategoryDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return "name is required and cannot be blank";
        }
        return null;
    }
}
