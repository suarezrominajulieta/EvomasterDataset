package org.menu.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.menu.api.domain.Food;
import org.menu.api.dto.ErrorDto;
import org.menu.api.dto.FoodDto;
import org.menu.api.dto.FoodPictureDetailDto;
import org.menu.api.dto.JsonPatchOperationDto;
import org.menu.api.dto.PictureMetadataDto;
import org.menu.api.dto.UploadResultDto;
import org.menu.api.dto.form.AllergenSheetForm;
import org.menu.api.dto.form.BatchPicturesForm;
import org.menu.api.dto.form.FoodsImportForm;
import org.menu.api.dto.form.SinglePictureForm;
import org.menu.api.dto.form.UploadFoodImageForm;
import org.menu.api.service.FoodImportService;
import org.menu.api.service.FoodPictureService;
import org.menu.api.service.FoodService;
import org.menu.api.service.JsonPatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/Foods", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Foods", description = "The dishes on the menu, and their pictures")
public class FoodsController {

    /**
     * Fields no patch may touch: the id, and the two derived views a dish carries. Pictures are
     * attached through the upload endpoints and the category through categoryId, so letting a
     * patch rewrite either of them would mean two ways of saying the same thing that could
     * disagree.
     */
    private static final Set<String> IMMUTABLE =
            Set.of("id", "pictures", "category", "deletedPictures", "allergenSheetPath");

    private final FoodService service;
    private final FoodPictureService pictures;
    private final FoodImportService imports;
    private final JsonPatchService patches;
    private final ObjectMapper mapper;

    public FoodsController(FoodService service,
                           FoodPictureService pictures,
                           FoodImportService imports,
                           JsonPatchService patches,
                           ObjectMapper mapper) {
        this.service = service;
        this.pictures = pictures;
        this.imports = imports;
        this.patches = patches;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "A page of dishes",
            description = """
                    The original took count and skip as optional path segments, which collided \
                    with the id route and made GET /Foods/{id} unreachable. They are query \
                    parameters here.""")
    @ApiResponse(responseCode = "200", description = "The page")
    @ApiResponse(responseCode = "400", description = "count or skip is out of range",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<FoodDto> getAll(
            @Parameter(description = "How many dishes to return, at most 200")
            @RequestParam(defaultValue = "10") int count,
            @Parameter(description = "How many to skip first")
            @RequestParam(defaultValue = "0") int skip) {
        return service.findAll(count, skip);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One dish, with its category and pictures")
    @ApiResponse(responseCode = "200", description = "The dish")
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public FoodDto getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a dish")
    @ApiResponse(responseCode = "201", description = "Created, with a Location header")
    @ApiResponse(responseCode = "400", description = "The document is not acceptable, or names a "
            + "category that does not exist",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<FoodDto> create(@Valid @RequestBody FoodDto body) {
        FoodDto created = service.create(body);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/Foods/{id}")
                        .buildAndExpand(created.getId())
                        .toUri())
                .body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a dish",
            description = "Answers 304 when the document matches what is already stored.")
    @ApiResponse(responseCode = "200", description = "The stored dish")
    @ApiResponse(responseCode = "304", description = "Nothing in the document differed")
    @ApiResponse(responseCode = "400", description = "The document is not acceptable, or its id "
            + "disagrees with the path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "deletedPictures names a picture that is not "
            + "attached to this dish",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<FoodDto> update(@PathVariable UUID id, @Valid @RequestBody FoodDto body) {
        FoodService.UpdateResult result = service.update(id, body);
        if (!result.modified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok(result.food());
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Patch a dish (RFC 6902)",
            description = "name, description, recept, price, currency and categoryId can move.")
    @ApiResponse(responseCode = "200", description = "The patched dish")
    @ApiResponse(responseCode = "400", description = "The patch is malformed, or produced a "
            + "document of the wrong shape",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The patch reaches for an immutable field",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "The patch does not apply, or names a "
            + "category that does not exist",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The patched document breaks a rule",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public FoodDto patch(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "A JSON Patch document",
                    content = @Content(mediaType = "application/json-patch+json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = JsonPatchOperationDto.class))))
            @RequestBody JsonNode patch) {
        Food food = service.get(id);
        FoodDto patched = patches.apply(patch, service.toDto(food), FoodDto.class, IMMUTABLE);
        return service.savePatched(food, patched);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a dish and its pictures")
    @ApiResponse(responseCode = "204", description = "Gone")
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- multipart -------------------------------------------------------------------------

    @PostMapping(path = "/UploadFoodImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach pictures to a dish named by a text part",
            description = "The shape the .NET original had: an array of file parts next to a "
                    + "plain text part carrying the dish id.")
    @ApiResponse(responseCode = "200", description = "What was stored")
    @ApiResponse(responseCode = "400", description = "foodId is missing or not a UUID, or a file "
            + "part is empty or nameless",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "A filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Two files share a name",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "Too many files, or one of them too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "A file is not an accepted image type",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "A file's bytes do not match its extension",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = UploadFoodImageForm.class),
                    encoding = {
                            @Encoding(name = "files", contentType = "image/jpeg, image/png, "
                                    + "image/gif, image/webp"),
                            @Encoding(name = "foodId", contentType = "text/plain")
                    }))
    public UploadResultDto uploadFoodImage(MultipartHttpServletRequest request) {
        return pictures.uploadMany(
                MultipartParts.requireText(request, "foodId"),
                MultipartParts.files(request, "files"));
    }

    @PostMapping(path = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach one picture, with an optional caption and position",
            description = "One file part next to two optional primitive parts.")
    @ApiResponse(responseCode = "201", description = "The stored picture")
    @ApiResponse(responseCode = "400", description = "The file part is missing, empty, nameless, "
            + "or displayOrder is not an integer",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "The file is too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The file is not an accepted image type",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The bytes do not match the extension, or "
            + "the caption or displayOrder is out of range",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = SinglePictureForm.class),
                    encoding = {
                            @Encoding(name = "caption", contentType = "text/plain"),
                            @Encoding(name = "displayOrder", contentType = "text/plain")
                    }))
    public ResponseEntity<FoodPictureDetailDto> uploadPicture(@PathVariable UUID id,
                                                              MultipartHttpServletRequest request) {
        FoodPictureDetailDto created = pictures.uploadOne(id,
                MultipartParts.requireFile(request, "file"),
                MultipartParts.text(request, "caption"),
                MultipartParts.integer(request, "displayOrder"));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/FoodPictures/{id}")
                        .buildAndExpand(created.getId())
                        .toUri())
                .body(created);
    }

    @PostMapping(path = "/{id}/pictures/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach several pictures, described by a JSON part",
            description = """
                    File parts next to an object part that travels as its own application/json \
                    section, as the encoding block says. The caption count has to match the file \
                    count, which is a branch only a correctly serialised JSON part reaches.""")
    @ApiResponse(responseCode = "200", description = "What was stored")
    @ApiResponse(responseCode = "400", description = "A part is missing or unreadable, or the "
            + "caption count does not match the file count",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "A filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Two files share a name",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "Too many files, or one of them too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The metadata part did not arrive as "
            + "application/json, or a file is not an accepted image type",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "A file's bytes do not match its extension",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = BatchPicturesForm.class),
                    encoding = {
                            @Encoding(name = "files", contentType = "image/jpeg, image/png, "
                                    + "image/gif, image/webp"),
                            @Encoding(name = "metadata", contentType = "application/json")
                    }))
    public UploadResultDto uploadPictureBatch(@PathVariable UUID id,
                                              MultipartHttpServletRequest request) {
        PictureMetadataDto metadata =
                MultipartParts.json(request, "metadata", PictureMetadataDto.class, mapper);
        return pictures.uploadBatch(id, MultipartParts.files(request, "files"), metadata);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import dishes from a CSV",
            description = """
                    A text/csv file part next to a boolean part. The CSV has the same format as \
                    src/main/resources/setup/dishes.csv, commas inside a description written as \
                    colons. With dryRun the document is parsed and checked but nothing is stored.""")
    @ApiResponse(responseCode = "200", description = "What was imported, or would have been")
    @ApiResponse(responseCode = "400", description = "The CSV is not this format, or dryRun is "
            + "not a boolean",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "A row names a category that does not exist",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "The CSV is too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The csv part did not arrive as text/csv",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "A row breaks a rule",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = FoodsImportForm.class),
                    encoding = {
                            @Encoding(name = "csv", contentType = "text/csv"),
                            @Encoding(name = "dryRun", contentType = "text/plain")
                    }))
    public FoodImportService.ImportResult importFoods(MultipartHttpServletRequest request) {
        return imports.importFrom(
                MultipartParts.requireFile(request, "csv"),
                MultipartParts.bool(request, "dryRun", false));
    }

    @PostMapping(path = "/{id}/allergens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the allergen sheet of a dish, as a PDF",
            description = """
                    One file part pinned to application/pdf by the encoding block. It has to start \
                    with %PDF- and carry a %%EOF trailer near the end; a second upload replaces the \
                    first. Its twin is the category icon: same shape, but that one wants a PNG, \
                    whose signature a text-encoded body cannot carry and this one's can.""")
    @ApiResponse(responseCode = "200", description = "The dish, with allergenSheetPath set")
    @ApiResponse(responseCode = "400", description = "The sheet part is missing, empty or nameless",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No dish with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "The sheet is too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The sheet part did not arrive as "
            + "application/pdf, or is not a .pdf file",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "No PDF header, or a header with no trailer",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = AllergenSheetForm.class),
                    encoding = @Encoding(name = "sheet", contentType = "application/pdf")))
    public FoodDto uploadAllergenSheet(@PathVariable UUID id, MultipartHttpServletRequest request) {
        return service.setAllergenSheet(id, MultipartParts.requireFile(request, "sheet"));
    }
}
