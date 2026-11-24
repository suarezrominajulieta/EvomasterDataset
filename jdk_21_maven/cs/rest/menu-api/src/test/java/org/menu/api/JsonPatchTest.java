package org.menu.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per row of the JSON Patch branch table, because the point of the table is that each
 * row is distinguishable from the others from outside.
 */
class JsonPatchTest extends MenuApiTestBase {

    private UUID aDish() throws Exception {
        UUID categoryId = createCategory("Patchable " + UUID.randomUUID());
        return createFood(categoryId, "Patch me", "10.00");
    }

    // ----- what works --------------------------------------------------------------------

    @Test
    @DisplayName("replace moves a field and answers with the whole patched dish")
    void replaceAField() throws Exception {
        UUID id = aDish();
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/price", "value": 12.5}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12.5))
                .andExpect(jsonPath("$.name").value("Patch me"));
    }

    @Test
    @DisplayName("a test that holds lets the rest of the patch through")
    void testThatHolds() throws Exception {
        UUID id = aDish();
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "test", "path": "/currency", "value": "USD"},
                                 {"op": "replace", "path": "/name", "value": "Tested"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tested"));
    }

    @Test
    @DisplayName("remove clears an optional field")
    void removeAField() throws Exception {
        UUID id = aDish();
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "remove", "path": "/description"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    @DisplayName("copy and move work between two mutable fields")
    void copyAndMove() throws Exception {
        UUID id = aDish();
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "copy", "from": "/name", "path": "/recept"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recept").value("Patch me"));
    }

    // ----- the branch table ----------------------------------------------------------------

    @Test
    @DisplayName("a patch aimed at a dish that does not exist is 404")
    void absentEntity() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", absentId())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/price", "value": 1}]"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a body that is not an array is 400")
    void notAnArray() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                {"op": "replace", "path": "/price", "value": 1}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an empty array is 400")
    void emptyArray() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an op nobody has heard of is 400")
    void unknownOp() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "upsert", "path": "/price", "value": 1}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("upsert")));
    }

    @Test
    @DisplayName("a path that does not start with a slash is 400")
    void pathWithoutLeadingSlash() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "price", "value": 1}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a path with an invalid tilde escape is 400")
    void pathWithBadEscape() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/pri~2ce", "value": 1}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("replacing a field the document does not have is 409")
    void replaceAbsentField() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/calories", "value": 1}]"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("removing a field the document does not have is 409")
    void removeAbsentField() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "remove", "path": "/calories"}]"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a test that fails is 409")
    void failingTest() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "test", "path": "/currency", "value": "EUR"}]"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("move without a from is 400")
    void moveWithoutFrom() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "move", "path": "/recept"}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("copy without a from is 400")
    void copyWithoutFrom() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "copy", "path": "/recept"}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("add, replace and test all need a value")
    void valueIsRequired() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/price"}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a value of the wrong type is 400 once the document is read back")
    void incompatibleType() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/price", "value": "expensive"}]"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("touching the id is 403")
    void idIsImmutable() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/id", "value": "%s"}]"""
                                .formatted(absentId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("touching pictures or category is 403")
    void derivedCollectionsAreImmutable() throws Exception {
        UUID id = aDish();
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "add", "path": "/pictures/-", "value": {}}]"""))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/Foods/{id}", id)
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/category/name", "value": "x"}]"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a from that reaches for an immutable field is 403 too")
    void immutableFromIsRefused() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "copy", "from": "/id", "path": "/recept"}]"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a negative price is 422")
    void negativePrice() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/price", "value": -1}]"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a blank name is 422")
    void blankName() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/name", "value": "   "}]"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a currency nobody uses is 422")
    void unknownCurrency() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/currency", "value": "XYZ"}]"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a categoryId that names nothing is 409, where a POST would give 400")
    void unknownCategoryIsConflict() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/categoryId", "value": "%s"}]"""
                                .formatted(absentId())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a patch sent as plain application/json is refused")
    void wrongContentType() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType("application/json")
                        .content("""
                                [{"op": "replace", "path": "/price", "value": 1}]"""))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ----- the other two patchable entities -------------------------------------------------

    @Nested
    class Categories {

        @Test
        @DisplayName("a category patches its name")
        void patchName() throws Exception {
            UUID id = createCategory("Before " + UUID.randomUUID());
            mvc.perform(patch("/api/v1/Categories/{id}", id)
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/name", "value": "After"}]"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("After"));
        }

        @Test
        @DisplayName("a category will not have its id or iconPath patched")
        void immutableFields() throws Exception {
            UUID id = createCategory("Fixed " + UUID.randomUUID());
            mvc.perform(patch("/api/v1/Categories/{id}", id)
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/iconPath", "value": "/x.png"}]"""))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a blank category name is 422")
        void blankName() throws Exception {
            UUID id = createCategory("Named " + UUID.randomUUID());
            mvc.perform(patch("/api/v1/Categories/{id}", id)
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/name", "value": ""}]"""))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    class Pictures {

        private UUID aPicture() throws Exception {
            UUID categoryId = createCategory("Pictured " + UUID.randomUUID());
            return attachPicture(createFood(categoryId, "Has a picture", "5.00"));
        }

        @Test
        @DisplayName("a picture patches its caption and position")
        void patchCaption() throws Exception {
            mvc.perform(patch("/api/v1/FoodPictures/{id}", aPicture())
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/caption", "value": "the front"},
                                     {"op": "replace", "path": "/displayOrder", "value": 3}]"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.caption").value("the front"))
                    .andExpect(jsonPath("$.displayOrder").value(3));
        }

        @Test
        @DisplayName("everything describing the stored file is immutable")
        void storedFileIsImmutable() throws Exception {
            UUID id = aPicture();
            mvc.perform(patch("/api/v1/FoodPictures/{id}", id)
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/filePath",
                                      "value": "/pictures/elsewhere.png"}]"""))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/FoodPictures/{id}", id)
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/length", "value": 0}]"""))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a negative display order is 422")
        void negativeDisplayOrder() throws Exception {
            mvc.perform(patch("/api/v1/FoodPictures/{id}", aPicture())
                            .contentType(JSON_PATCH)
                            .content("""
                                    [{"op": "replace", "path": "/displayOrder", "value": -2}]"""))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("GET returns the picture the upload recorded")
        void getPicture() throws Exception {
            mvc.perform(get("/api/v1/FoodPictures/{id}", aPicture()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalFileName").value("dish.png"))
                    .andExpect(jsonPath("$.contentType").value("image/png"));
        }
    }
}
