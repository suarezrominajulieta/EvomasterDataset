package org.menu.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.menu.api.domain.Category;
import org.menu.api.dto.CategoryDto;
import org.menu.api.dto.ErrorDto;
import org.menu.api.dto.JsonPatchOperationDto;
import org.menu.api.dto.form.CategoryIconForm;
import org.menu.api.service.CategoryService;
import org.menu.api.service.JsonPatchService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/Categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Categories", description = "The sections a menu is divided into")
public class CategoriesController {

    /** Fields the server owns. A patch that reaches for one is a 403. */
    private static final Set<String> IMMUTABLE = Set.of("id", "iconPath");

    private final CategoryService service;
    private final JsonPatchService patches;

    public CategoriesController(CategoryService service, JsonPatchService patches) {
        this.service = service;
        this.patches = patches;
    }

    @GetMapping
    @Operation(summary = "Every category")
    public List<CategoryDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One category")
    @ApiResponse(responseCode = "200", description = "The category")
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CategoryDto getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Created, with a Location header")
    @ApiResponse(responseCode = "400", description = "The document is not acceptable",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryDto body) {
        // The original answered 200 with no body, which left a client without the id of what it
        // had just made. Handing back a Location is what makes the PATCH and upload endpoints
        // reachable from a standing start.
        CategoryDto created = service.create(body);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/Categories/{id}")
                        .buildAndExpand(created.getId())
                        .toUri())
                .body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a category")
    @ApiResponse(responseCode = "200", description = "The stored category")
    @ApiResponse(responseCode = "400", description = "The document is not acceptable, or its id "
            + "disagrees with the path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CategoryDto update(@PathVariable UUID id, @Valid @RequestBody CategoryDto body) {
        return service.update(id, body);
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Patch a category (RFC 6902)",
            description = "Only name and color can move; id and iconPath are the server's.")
    @ApiResponse(responseCode = "200", description = "The patched category")
    @ApiResponse(responseCode = "400", description = "The patch is malformed",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The patch reaches for an immutable field",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "The patch does not apply to this document",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The patched document breaks a rule",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CategoryDto patch(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "A JSON Patch document",
                    content = @Content(mediaType = "application/json-patch+json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = JsonPatchOperationDto.class))))
            @RequestBody JsonNode patch) {
        Category category = service.get(id);
        CategoryDto patched = patches.apply(patch, service.toDto(category), CategoryDto.class, IMMUTABLE);
        return service.savePatched(category, patched);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category, and with it its dishes and their pictures")
    @ApiResponse(responseCode = "204", description = "Gone")
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a category icon",
            description = "The icon part has to arrive as image/png, as the encoding block says.")
    @ApiResponse(responseCode = "200", description = "The category, with iconPath set")
    @ApiResponse(responseCode = "400", description = "A part is missing, empty, or not a file",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "The icon is too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The icon part did not arrive as image/png",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The bytes are not a PNG",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = CategoryIconForm.class),
                    encoding = {
                            @io.swagger.v3.oas.annotations.media.Encoding(
                                    name = "icon", contentType = "image/png"),
                            @io.swagger.v3.oas.annotations.media.Encoding(
                                    name = "color", contentType = "text/plain")
                    }))
    public CategoryDto uploadIcon(@PathVariable UUID id, MultipartHttpServletRequest request) {
        return service.setIcon(id,
                MultipartParts.requireFile(request, "icon"),
                MultipartParts.text(request, "color"));
    }
}
