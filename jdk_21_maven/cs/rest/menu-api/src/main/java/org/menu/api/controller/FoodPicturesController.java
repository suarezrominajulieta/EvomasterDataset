package org.menu.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.menu.api.domain.FoodPicture;
import org.menu.api.dto.ErrorDto;
import org.menu.api.dto.FoodPictureDetailDto;
import org.menu.api.dto.JsonPatchOperationDto;
import org.menu.api.dto.form.PictureContentForm;
import org.menu.api.service.FoodPictureService;
import org.menu.api.service.JsonPatchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/FoodPictures", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "FoodPictures", description = "The pictures attached to dishes")
public class FoodPicturesController {

    /** Everything that describes the stored file. Only caption and displayOrder can move. */
    private static final Set<String> IMMUTABLE =
            Set.of("id", "filePath", "originalFileName", "length", "contentType", "foodId");

    private final FoodPictureService service;
    private final JsonPatchService patches;

    public FoodPicturesController(FoodPictureService service, JsonPatchService patches) {
        this.service = service;
        this.patches = patches;
    }

    @GetMapping("/{id}")
    @Operation(summary = "One picture, in full")
    @ApiResponse(responseCode = "200", description = "The picture")
    @ApiResponse(responseCode = "404", description = "No picture with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public FoodPictureDetailDto getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Patch a picture (RFC 6902)",
            description = "Only caption and displayOrder can move; everything that describes the "
                    + "stored file is the server's.")
    @ApiResponse(responseCode = "200", description = "The patched picture")
    @ApiResponse(responseCode = "400", description = "The patch is malformed, or produced a "
            + "document of the wrong shape",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The patch reaches for an immutable field",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No picture with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "The patch does not apply to this document",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The patched document breaks a rule",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public FoodPictureDetailDto patch(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "A JSON Patch document",
                    content = @Content(mediaType = "application/json-patch+json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = JsonPatchOperationDto.class))))
            @RequestBody JsonNode patch) {
        FoodPicture picture = service.get(id);
        FoodPictureDetailDto patched = patches.apply(
                patch, service.toDto(picture), FoodPictureDetailDto.class, IMMUTABLE);
        return service.savePatched(picture, patched);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Detach a picture and delete its file")
    @ApiResponse(responseCode = "204", description = "Gone")
    @ApiResponse(responseCode = "404", description = "No picture with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace a picture's bytes",
            description = "A multipart body on a PUT rather than a POST, which is the point of "
                    + "this endpoint existing at all.")
    @ApiResponse(responseCode = "200", description = "The picture, now pointing at the new file")
    @ApiResponse(responseCode = "400", description = "The file part is missing, empty or nameless",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "The filename carries a path",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "No picture with that id",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "413", description = "The file is too large",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "415", description = "The file is not an accepted image type",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "The bytes do not match the extension",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = PictureContentForm.class)))
    public FoodPictureDetailDto replaceContent(@PathVariable UUID id,
                                               MultipartHttpServletRequest request) {
        return service.replaceContent(id, MultipartParts.requireFile(request, "file"));
    }
}
