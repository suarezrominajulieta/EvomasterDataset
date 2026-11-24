package org.menu.api.service;

import org.menu.api.domain.Food;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.FoodPictureDetailDto;
import org.menu.api.dto.FoodPictureDto;
import org.menu.api.dto.PictureMetadataDto;
import org.menu.api.dto.UploadResultDto;
import org.menu.api.mapper.ModelsMapper;
import org.menu.api.repository.FoodPictureRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything that attaches bytes to a dish, and everything that reads those rows back. */
@Service
@Transactional
public class FoodPictureService {

    private final FoodPictureRepository repository;
    private final FoodService foods;
    private final FileStorageService storage;
    private final FileValidationService validation;
    private final ModelsMapper mapper;
    private final int maxPerFood;

    public FoodPictureService(FoodPictureRepository repository,
                              FoodService foods,
                              FileStorageService storage,
                              FileValidationService validation,
                              ModelsMapper mapper,
                              @Value("${menu.pictures.max-per-food:25}") int maxPerFood) {
        this.repository = repository;
        this.foods = foods;
        this.storage = storage;
        this.validation = validation;
        this.mapper = mapper;
        this.maxPerFood = maxPerFood;
    }

    public FoodPicture get(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("picture", id));
    }

    @Transactional(readOnly = true)
    public FoodPictureDetailDto findById(UUID id) {
        return mapper.toDetailDto(get(id));
    }

    public FoodPictureDetailDto toDto(FoodPicture picture) {
        return mapper.toDetailDto(picture);
    }

    public void delete(UUID id) {
        FoodPicture picture = get(id);
        Food food = picture.getFood();
        if (food != null) {
            food.getPictures().remove(picture);
        }
        storage.delete(picture.getFileName());
        repository.delete(picture);
    }

    /** Writes a patched picture back. Only caption and ordering can have moved. */
    public FoodPictureDetailDto savePatched(FoodPicture picture, FoodPictureDetailDto dto) {
        if (dto.getCaption() != null && dto.getCaption().length() > 255) {
            throw new UnprocessableEntityException("caption cannot be longer than 255 characters");
        }
        if (dto.getDisplayOrder() != null && dto.getDisplayOrder() < 0) {
            throw new UnprocessableEntityException("displayOrder cannot be negative");
        }
        picture.setCaption(dto.getCaption());
        picture.setDisplayOrder(dto.getDisplayOrder());
        return mapper.toDetailDto(repository.save(picture));
    }

    /**
     * {@code POST /api/v1/Foods/UploadFoodImage}: the shape the .NET original had, with the dish
     * named by a text part rather than by the path.
     */
    public UploadResultDto uploadMany(String foodId, List<MultipartFile> files) {
        if (foodId == null || foodId.isBlank()) {
            throw new InvalidRequestException("foodId is required");
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(foodId.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("foodId " + foodId + " is not a UUID");
        }
        Food food = foods.get(parsed);
        validation.validateAll(files, "files");
        return attachAll(food, files, null);
    }

    /** {@code POST /api/v1/Foods/{id}/picture}: one file, plus two optional primitive parts. */
    public FoodPictureDetailDto uploadOne(UUID foodId,
                                          MultipartFile file,
                                          String caption,
                                          Integer displayOrder) {
        Food food = foods.get(foodId);
        validation.validate(file, "file");
        if (caption != null && caption.length() > 255) {
            throw new UnprocessableEntityException("caption cannot be longer than 255 characters");
        }
        if (displayOrder != null && displayOrder < 0) {
            throw new UnprocessableEntityException("displayOrder cannot be negative");
        }
        FoodPicture picture = attach(food, file);
        picture.setCaption(caption);
        picture.setDisplayOrder(displayOrder);
        trim(food);
        foods.save(food);
        return mapper.toDetailDto(picture);
    }

    /**
     * {@code POST /api/v1/Foods/{id}/pictures/batch}: files next to a JSON part.
     *
     * <p>The caption count has to match the file count. That branch is only reachable when the
     * JSON part actually arrived as JSON, which is the thing this endpoint is here to tell apart.
     */
    public UploadResultDto uploadBatch(UUID foodId,
                                       List<MultipartFile> files,
                                       PictureMetadataDto metadata) {
        Food food = foods.get(foodId);
        validation.validateAll(files, "files");
        if (metadata == null) {
            throw new InvalidRequestException("part metadata is required");
        }
        List<String> captions = metadata.getCaptions();
        if (captions != null && captions.size() != files.size()) {
            throw new InvalidRequestException(
                    "metadata carries " + captions.size() + " captions for " + files.size()
                            + " files");
        }
        if (Boolean.TRUE.equals(metadata.getReplaceExisting())) {
            for (FoodPicture existing : List.copyOf(food.getPictures())) {
                storage.delete(existing.getFileName());
                food.getPictures().remove(existing);
            }
        }
        return attachAll(food, files, captions);
    }

    /** {@code PUT /api/v1/FoodPictures/{id}/content}: new bytes behind an existing row. */
    public FoodPictureDetailDto replaceContent(UUID id, MultipartFile file) {
        FoodPicture picture = get(id);
        validation.validate(file, "file");
        String previous = picture.getFileName();
        picture.setFileName(storage.store(file));
        picture.setOriginalFileName(file.getOriginalFilename());
        picture.setLength(file.getSize());
        picture.setContentType(file.getContentType());
        storage.delete(previous);
        return mapper.toDetailDto(repository.save(picture));
    }

    private UploadResultDto attachAll(Food food, List<MultipartFile> files, List<String> captions) {
        List<FoodPictureDto> created = new ArrayList<>();
        long bytes = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            FoodPicture picture = attach(food, file);
            if (captions != null) {
                picture.setCaption(captions.get(i));
            }
            picture.setDisplayOrder(i);
            created.add(mapper.toDto(picture));
            bytes += file.getSize();
        }
        trim(food);
        foods.save(food);
        return new UploadResultDto(created.size(), bytes, created);
    }

    /**
     * Drops the oldest pictures of a dish until it is back inside its limit, file and row
     * together.
     *
     * <p>Without this the row count is unbounded even though the file store is not: a client
     * that keeps uploading to one dish makes every later {@code GET /Foods} carry every picture
     * it ever sent, at about 127 bytes each, for the rest of the run. Those bytes buy no
     * coverage and cost time on an endpoint a fuzzer calls constantly.
     *
     * <p>As with the file store this evicts rather than refusing, so no status code depends on
     * how much a run has already uploaded, and the newest upload is always the one kept.
     */
    private void trim(Food food) {
        List<FoodPicture> pictures = food.getPictures();
        while (pictures.size() > maxPerFood) {
            // The list is ordered oldest first, so index 0 is the one to lose. orphanRemoval
            // on the relation turns the removal into a delete.
            FoodPicture oldest = pictures.remove(0);
            storage.delete(oldest.getFileName());
        }
    }

    /** Stores the bytes and hangs a row off the dish. The caller has already validated. */
    private FoodPicture attach(Food food, MultipartFile file) {
        FoodPicture picture = new FoodPicture();
        picture.setId(UUID.randomUUID());
        picture.setFileName(storage.store(file));
        picture.setOriginalFileName(file.getOriginalFilename());
        picture.setLength(file.getSize());
        picture.setContentType(file.getContentType());
        food.addPicture(picture);
        return picture;
    }
}
