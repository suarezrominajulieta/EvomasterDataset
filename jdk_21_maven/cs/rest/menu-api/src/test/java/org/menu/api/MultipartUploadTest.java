package org.menu.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The six multipart endpoints, and one test per row of the file validation table.
 *
 * <p>The table is the reason this project exists: every rejection has a status code of its own,
 * so a client that gets the body right and a client that gets it wrong can be told apart from
 * the outside without reading any logs.
 */
class MultipartUploadTest extends MenuApiTestBase {

    private UUID aDish() throws Exception {
        UUID categoryId = createCategory("Uploadable " + UUID.randomUUID());
        return createFood(categoryId, "Photogenic", "6.00");
    }

    // ----- (1) POST /Foods/UploadFoodImage --------------------------------------------------

    @Nested
    class UploadFoodImage {

        @Test
        @DisplayName("an array of files next to a text part stores them all")
        void uploadsSeveral() throws Exception {
            UUID foodId = aDish();
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .file(pngPart("files", "one.png"))
                            .file(jpegPart("files", "two.jpg"))
                            .param("foodId", foodId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploaded").value(2))
                    .andExpect(jsonPath("$.pictures.length()").value(2));

            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(jsonPath("$.pictures.length()").value(2));
        }

        @Test
        @DisplayName("a foodId that is not a UUID is 400")
        void malformedFoodId() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .file(pngPart("files", "one.png"))
                            .param("foodId", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a missing foodId is 400")
        void missingFoodId() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .file(pngPart("files", "one.png")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a dish that does not exist is 404")
        void absentFood() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .file(pngPart("files", "one.png"))
                            .param("foodId", absentId().toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("no files at all is 400")
        void noFiles() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .param("foodId", aDish().toString()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----- (2) POST /Foods/{id}/picture ------------------------------------------------------

    @Nested
    class SinglePicture {

        @Test
        @DisplayName("one file with both optional parts answers 201")
        void uploadsWithOptionalParts() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/picture", aDish())
                            .file(pngPart("file", "front.png"))
                            .param("caption", "the front")
                            .param("displayOrder", "2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.caption").value("the front"))
                    .andExpect(jsonPath("$.displayOrder").value(2));
        }

        @Test
        @DisplayName("the optional parts really are optional")
        void uploadsWithoutOptionalParts() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/picture", aDish())
                            .file(pngPart("file", "bare.png")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.caption").doesNotExist());
        }

        @Test
        @DisplayName("a displayOrder that is not an integer is 400")
        void unparseableDisplayOrder() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/picture", aDish())
                            .file(pngPart("file", "front.png"))
                            .param("displayOrder", "second"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a negative displayOrder is 422")
        void negativeDisplayOrder() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/picture", aDish())
                            .file(pngPart("file", "front.png"))
                            .param("displayOrder", "-1"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("a dish that does not exist is 404, before any byte is looked at")
        void absentFood() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/picture", absentId())
                            .file(pngPart("file", "front.png")))
                    .andExpect(status().isNotFound());
        }
    }

    // ----- (3) POST /Foods/{id}/pictures/batch ----------------------------------------------

    @Nested
    class BatchWithJsonPart {

        @Test
        @DisplayName("files next to a JSON part store with their captions")
        void uploadsWithMetadata() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", aDish())
                            .file(pngPart("files", "one.png"))
                            .file(jpegPart("files", "two.jpg"))
                            .part(typedPart("metadata", """
                                    {"captions": ["front", "back"], "replaceExisting": false}""",
                                    "application/json")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploaded").value(2));
        }

        @Test
        @DisplayName("a caption count that misses the file count is 400")
        void captionCountMismatch() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", aDish())
                            .file(pngPart("files", "one.png"))
                            .file(jpegPart("files", "two.jpg"))
                            .part(typedPart("metadata", """
                                    {"captions": ["only one"]}""", "application/json")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("captions")));
        }

        @Test
        @DisplayName("a metadata part sent as plain text is 415")
        void metadataWithoutJsonContentType() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", aDish())
                            .file(pngPart("files", "one.png"))
                            .part(typedPart("metadata", """
                                    {"captions": ["front"]}""", "text/plain")))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("a metadata part that is not readable JSON is 400")
        void unreadableMetadata() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", aDish())
                            .file(pngPart("files", "one.png"))
                            .part(typedPart("metadata", "{not json", "application/json")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a missing metadata part is 400")
        void missingMetadata() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", aDish())
                            .file(pngPart("files", "one.png")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("replaceExisting clears what was attached before")
        void replaceExisting() throws Exception {
            UUID foodId = aDish();
            attachPicture(foodId);

            mvc.perform(multipart("/api/v1/Foods/{id}/pictures/batch", foodId)
                            .file(pngPart("files", "new.png"))
                            .part(typedPart("metadata", """
                                    {"replaceExisting": true}""", "application/json")))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(jsonPath("$.pictures.length()").value(1));
        }
    }

    // ----- (4) POST /Categories/{id}/icon ----------------------------------------------------

    @Nested
    class CategoryIcon {

        @Test
        @DisplayName("a PNG icon next to a colour is stored")
        void uploadsIcon() throws Exception {
            UUID id = createCategory("Iconic " + UUID.randomUUID());
            mvc.perform(multipart("/api/v1/Categories/{id}/icon", id)
                            .file(pngPart("icon", "icon.png"))
                            .param("color", "Teal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.color").value("Teal"))
                    .andExpect(jsonPath("$.iconPath").isNotEmpty());
        }

        @Test
        @DisplayName("an icon part arriving as anything but image/png is 415")
        void wrongPartContentType() throws Exception {
            UUID id = createCategory("Fussy " + UUID.randomUUID());
            mvc.perform(multipart("/api/v1/Categories/{id}/icon", id)
                            .file(jpegPart("icon", "icon.jpg")))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("a category that does not exist is 404")
        void absentCategory() throws Exception {
            mvc.perform(multipart("/api/v1/Categories/{id}/icon", absentId())
                            .file(pngPart("icon", "icon.png")))
                    .andExpect(status().isNotFound());
        }
    }

    // ----- (5) POST /Foods/import ------------------------------------------------------------

    @Nested
    class CsvImport {

        private static final String CSV = """
                DishID,Type,Name,Description,Price
                90,Salads,Imported Salad,Leaves: seeds: and dressing,5.50
                91,Salads,Second Salad,More leaves,6.50
                """;

        private MockMultipartFile csv(String content) {
            return new MockMultipartFile("csv", "dishes.csv", "text/csv",
                    content.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("a CSV of dishes is imported, and colons come back as commas")
        void imports() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv(CSV))
                            .param("dryRun", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imported").value(2))
                    .andExpect(jsonPath("$.dryRun").value(false))
                    .andExpect(jsonPath("$.foods[0].description")
                            .value("Leaves, seeds, and dressing"));
        }

        @Test
        @DisplayName("a dry run validates and stores nothing")
        void dryRun() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv(CSV))
                            .param("dryRun", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imported").value(2))
                    .andExpect(jsonPath("$.dryRun").value(true));
        }

        @Test
        @DisplayName("dryRun defaults to false when the part is absent")
        void dryRunDefaults() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import").file(csv(CSV)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dryRun").value(false));
        }

        @Test
        @DisplayName("a dryRun that is not a boolean is 400")
        void unparseableDryRun() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv(CSV))
                            .param("dryRun", "perhaps"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("the wrong header is 400")
        void wrongHeader() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("a,b,c\n1,2,3\n")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a row with the wrong column count is 400")
        void wrongColumnCount() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("""
                                    DishID,Type,Name,Description,Price
                                    90,Salads,Short row
                                    """)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an unreadable price is 400")
        void unreadablePrice() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("""
                                    DishID,Type,Name,Description,Price
                                    90,Salads,Odd,Costs a lot,lots
                                    """)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a row naming a category that does not exist is 409")
        void unknownCategory() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("""
                                    DishID,Type,Name,Description,Price
                                    90,Nonexistent,Lost,Nowhere to go,1.00
                                    """)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("a row with a negative price is 422")
        void negativePrice() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("""
                                    DishID,Type,Name,Description,Price
                                    90,Salads,Refunded,We pay you,-1.00
                                    """)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("a csv part not sent as text/csv is 415")
        void wrongPartContentType() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(new MockMultipartFile("csv", "dishes.csv", "text/plain",
                                    CSV.getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("a header with no rows behind it is 400")
        void headerOnly() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/import")
                            .file(csv("DishID,Type,Name,Description,Price\n")))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----- (6) PUT /FoodPictures/{id}/content ------------------------------------------------

    @Nested
    class ReplaceContent {

        private MockMultipartHttpServletRequestBuilder putMultipart(String path, Object... vars) {
            MockMultipartHttpServletRequestBuilder builder = multipart(path, vars);
            builder.with(request -> {
                request.setMethod(HttpMethod.PUT.name());
                return request;
            });
            return builder;
        }

        @Test
        @DisplayName("a multipart body on a PUT replaces the stored bytes")
        void replaces() throws Exception {
            UUID pictureId = attachPicture(aDish());
            mvc.perform(putMultipart("/api/v1/FoodPictures/{id}/content", pictureId)
                            .file(jpegPart("file", "replacement.jpg")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalFileName").value("replacement.jpg"))
                    .andExpect(jsonPath("$.contentType").value("image/jpeg"));
        }

        @Test
        @DisplayName("a picture that does not exist is 404")
        void absentPicture() throws Exception {
            mvc.perform(putMultipart("/api/v1/FoodPictures/{id}/content", absentId())
                            .file(pngPart("file", "x.png")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a missing file part is 400")
        void missingFile() throws Exception {
            UUID pictureId = attachPicture(aDish());
            mvc.perform(putMultipart("/api/v1/FoodPictures/{id}/content", pictureId))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----- how many a dish may keep ------------------------------------------------------------

    @Nested
    class PicturesPerDish {

        /** Matches menu.pictures.max-per-food, which the tests leave at its default. */
        private static final int CAP = 25;

        @Test
        @DisplayName("a dish keeps at most the cap, and keeps the newest")
        void oldestGo() throws Exception {
            UUID foodId = aDish();
            String lastName = null;
            for (int i = 0; i < CAP + 12; i++) {
                lastName = "shot" + i + ".png";
                mvc.perform(multipart("/api/v1/Foods/{id}/picture", foodId)
                                .file(pngPart("file", lastName)))
                        .andExpect(status().isCreated());
            }

            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pictures.length()").value(CAP));

            // The newest upload is never the one evicted.
            String stored = lastName;
            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(jsonPath("$.pictures[" + (CAP - 1) + "].filePath")
                            .value(org.hamcrest.Matchers.containsString(
                                    stored.replace(".png", ""))));
        }

        @Test
        @DisplayName("a batch that overshoots the cap in one request is trimmed too")
        void batchIsTrimmed() throws Exception {
            UUID foodId = aDish();
            for (int round = 0; round < 4; round++) {
                var builder = multipart("/api/v1/Foods/UploadFoodImage");
                for (int i = 0; i < 10; i++) {
                    builder = builder.file(pngPart("files", "r" + round + "n" + i + ".png"));
                }
                mvc.perform(builder.param("foodId", foodId.toString()))
                        .andExpect(status().isOk());
            }

            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(jsonPath("$.pictures.length()").value(CAP));
        }

        @Test
        @DisplayName("an evicted picture is gone from its own endpoint as well")
        void evictedRowIsDeleted() throws Exception {
            UUID foodId = aDish();
            UUID first = attachPicture(foodId);
            mvc.perform(get("/api/v1/FoodPictures/{id}", first)).andExpect(status().isOk());

            for (int i = 0; i < CAP; i++) {
                mvc.perform(multipart("/api/v1/Foods/{id}/picture", foodId)
                                .file(pngPart("file", "push" + i + ".png")))
                        .andExpect(status().isCreated());
            }

            mvc.perform(get("/api/v1/FoodPictures/{id}", first))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("pictures come back oldest first, not in whatever order the database likes")
        void orderIsDefined() throws Exception {
            UUID foodId = aDish();
            for (int i = 0; i < 5; i++) {
                mvc.perform(multipart("/api/v1/Foods/{id}/picture", foodId)
                                .file(pngPart("file", "n" + i + ".png"))
                                .param("caption", "c" + i))
                        .andExpect(status().isCreated());
            }

            mvc.perform(get("/api/v1/Foods/{id}", foodId))
                    .andExpect(jsonPath("$.pictures[0].filePath")
                            .value(org.hamcrest.Matchers.containsString("n0")))
                    .andExpect(jsonPath("$.pictures[4].filePath")
                            .value(org.hamcrest.Matchers.containsString("n4")));
        }
    }

    // ----- the validation table --------------------------------------------------------------

    @Nested
    class FileRules {

        /** Every rule is exercised through the single picture endpoint, which is the simplest. */
        private MockMultipartHttpServletRequestBuilder upload(UUID foodId) {
            return multipart("/api/v1/Foods/{id}/picture", foodId);
        }

        @Test
        @DisplayName("a required part that is absent is 400")
        void missingPart() throws Exception {
            mvc.perform(upload(aDish()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("required")));
        }

        @Test
        @DisplayName("a part that arrived as a form field, with no filename, says so")
        void partWithoutFilename() throws Exception {
            mvc.perform(upload(aDish()).param("file", "just some text"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("without a filename")));
        }

        @Test
        @DisplayName("a file part whose filename is empty says the same")
        void emptyFilename() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "", "image/png", png())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("without a filename")));
        }

        @Test
        @DisplayName("an extension outside the list is 415")
        void forbiddenExtension() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "shell.jsp", "image/png", png())))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("a file with no extension at all is 415")
        void noExtension() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "picture", "image/png", png())))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("a double extension is 415")
        void doubleExtension() throws Exception {
            // The last extension is what decides the type, so shell.png.jsp is already caught by
            // the allowed-extension rule above. This branch is the other spelling of the trick:
            // an acceptable extension on the outside with something else hidden behind it.
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "shell.jsp.png", "image/png",
                                    png())))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("more than one extension")));
        }

        @Test
        @DisplayName("the other spelling, shell.png.jsp, is 415 through the extension list")
        void trailingForbiddenExtension() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "shell.png.jsp", "image/png",
                                    png())))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("accepted image extensions")));
        }

        @Test
        @DisplayName("an ordinary name with a dot in it is still accepted")
        void dottedNameIsFine() throws Exception {
            // The rule only fires when what precedes the last dot reads as an extension too, so
            // a name like this one is not mistaken for the trick above.
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "creme.brulee.png", "image/png",
                                    png())))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("a filename that climbs out of the directory is 403")
        void pathTraversal() throws Exception {
            UUID foodId = aDish();
            mvc.perform(upload(foodId)
                            .file(new MockMultipartFile("file", "../escape.png", "image/png",
                                    png())))
                    .andExpect(status().isForbidden());
            mvc.perform(upload(foodId)
                            .file(new MockMultipartFile("file", "..\\escape.png", "image/png",
                                    png())))
                    .andExpect(status().isForbidden());
            mvc.perform(upload(foodId)
                            .file(new MockMultipartFile("file", "C:\\windows\\evil.png",
                                    "image/png", png())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a filename carrying a control character or a null byte is 400")
        void controlCharacterInFilename() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "ev\u0000il.png", "image/png",
                                    png())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a filename longer than 255 characters is 400")
        void overlongFilename() throws Exception {
            String name = "a".repeat(260) + ".png";
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", name, "image/png", png())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a part content type outside the image list is 415")
        void forbiddenContentType() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "picture.png", "application/pdf",
                                    png())))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("bytes that do not match the extension are 422")
        void magicBytesMismatch() throws Exception {
            UUID foodId = aDish();
            mvc.perform(upload(foodId)
                            .file(new MockMultipartFile("file", "lying.png", "image/png",
                                    notAnImage())))
                    .andExpect(status().isUnprocessableEntity());
            // A real GIF renamed to .png is caught the same way.
            mvc.perform(upload(foodId)
                            .file(new MockMultipartFile("file", "actually.png", "image/png",
                                    gif())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("a GIF that says it is a GIF is accepted")
        void gifIsAccepted() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "animated.gif", "image/gif",
                                    gif())))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("an empty file is 400")
        void emptyFile() throws Exception {
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "empty.png", "image/png",
                                    new byte[0])))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a file past the application limit is 413")
        void fileTooLarge() throws Exception {
            byte[] big = new byte[6 * 1024 * 1024];
            System.arraycopy(png(), 0, big, 0, 8);
            mvc.perform(upload(aDish())
                            .file(new MockMultipartFile("file", "huge.png", "image/png", big)))
                    .andExpect(status().isPayloadTooLarge());
        }

        @Test
        @DisplayName("more than ten files in one request is 413")
        void tooManyFiles() throws Exception {
            var builder = multipart("/api/v1/Foods/UploadFoodImage");
            for (int i = 0; i < 11; i++) {
                builder = builder.file(pngPart("files", "picture" + i + ".png"));
            }
            mvc.perform(builder.param("foodId", aDish().toString()))
                    .andExpect(status().isPayloadTooLarge());
        }

        @Test
        @DisplayName("the same filename twice in one request is 409")
        void duplicateFilename() throws Exception {
            mvc.perform(multipart("/api/v1/Foods/UploadFoodImage")
                            .file(pngPart("files", "same.png"))
                            .file(pngPart("files", "SAME.PNG"))
                            .param("foodId", aDish().toString()))
                    .andExpect(status().isConflict());
        }
    }
}
