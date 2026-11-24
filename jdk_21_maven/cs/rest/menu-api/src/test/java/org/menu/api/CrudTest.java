package org.menu.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The base API: the CRUD the .NET original had, with the status codes this port gives it. */
class CrudTest extends MenuApiTestBase {

    // ----- categories --------------------------------------------------------------------

    @Test
    @DisplayName("the seed leaves five categories to read")
    void categoriesAreSeeded() throws Exception {
        mvc.perform(get("/api/v1/Categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Salads')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Desert')]").exists());
    }

    @Test
    @DisplayName("a created category answers 201 with a Location header")
    void createCategory() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/Categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sides", "color": "Green"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Sides"))
                .andExpect(jsonPath("$.color").value("Green"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a category without a name is refused")
    void createCategoryWithoutName() throws Exception {
        mvc.perform(post("/api/v1/Categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"color": "Green"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("an unknown category id is 404, not 204")
    void unknownCategoryIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/Categories/{id}", absentId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("an id that is not a UUID names no resource either")
    void malformedCategoryIdIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/Categories/{id}", "not-a-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a PUT whose body id disagrees with the path is refused")
    void putWithMismatchedId() throws Exception {
        UUID id = createCategory("Brunch");
        mvc.perform(put("/api/v1/Categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "name": "Brunch", "color": "Blue"}"""
                                .formatted(absentId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deleting a category takes its dishes with it")
    void deleteCascades() throws Exception {
        UUID categoryId = createCategory("Ephemeral");
        UUID foodId = createFood(categoryId, "Short lived", "1.00");

        mvc.perform(delete("/api/v1/Categories/{id}", categoryId))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/Foods/{id}", foodId))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/Categories/{id}", categoryId))
                .andExpect(status().isNotFound());
    }

    // ----- foods -------------------------------------------------------------------------

    @Test
    @DisplayName("dishes page through query parameters, not path segments")
    void foodsArePaged() throws Exception {
        mvc.perform(get("/api/v1/Foods").param("count", "3").param("skip", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("a negative count is a 400, not a driver error")
    void negativeCountIsRefused() throws Exception {
        mvc.perform(get("/api/v1/Foods").param("count", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("count")));
        mvc.perform(get("/api/v1/Foods").param("skip", "-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a count that is not a number is a 400")
    void unparseableCountIsRefused() throws Exception {
        mvc.perform(get("/api/v1/Foods").param("count", "many"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /Foods/{id} is reachable, which it was not in the original")
    void singleFoodIsReachable() throws Exception {
        UUID categoryId = createCategory("Readable");
        UUID foodId = createFood(categoryId, "A dish", "4.50");

        mvc.perform(get("/api/v1/Foods/{id}", foodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A dish"))
                .andExpect(jsonPath("$.category.name").value("Readable"))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    @DisplayName("a dish naming a category that does not exist is refused")
    void foodWithUnknownCategory() throws Exception {
        mvc.perform(post("/api/v1/Foods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Orphan", "price": 1.00, "currency": "USD",
                                 "categoryId": "%s"}""".formatted(absentId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a dish priced in a currency nobody uses is refused")
    void foodWithUnknownCurrency() throws Exception {
        UUID categoryId = createCategory("Priced");
        mvc.perform(post("/api/v1/Foods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Odd", "price": 1.00, "currency": "XYZ",
                                 "categoryId": "%s"}""".formatted(categoryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a PUT that changes nothing answers 304")
    void unchangedPutIsNotModified() throws Exception {
        UUID categoryId = createCategory("Static");
        UUID foodId = createFood(categoryId, "Unchanging", "7.25");

        String body = """
                {"id": "%s", "name": "Unchanging", "description": "a dish", "price": 7.25,
                 "currency": "USD", "categoryId": "%s"}""".formatted(foodId, categoryId);

        mvc.perform(put("/api/v1/Foods/{id}", foodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("a PUT that changes something answers 200 with the stored dish")
    void changedPutIsOk() throws Exception {
        UUID categoryId = createCategory("Moving");
        UUID foodId = createFood(categoryId, "Changes", "7.25");

        mvc.perform(put("/api/v1/Foods/{id}", foodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "name": "Changed", "description": "a dish",
                                 "price": 9.00, "currency": "USD", "categoryId": "%s"}"""
                                .formatted(foodId, categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Changed"))
                .andExpect(jsonPath("$.price").value(9.00));
    }

    @Test
    @DisplayName("a PUT naming a picture that is not attached is a conflict")
    void putWithForeignDeletedPicture() throws Exception {
        UUID categoryId = createCategory("Detaching");
        UUID foodId = createFood(categoryId, "Has no pictures", "3.00");

        mvc.perform(put("/api/v1/Foods/{id}", foodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "name": "Has no pictures", "description": "a dish",
                                 "price": 3.00, "currency": "USD", "categoryId": "%s",
                                 "deletedPictures": [{"id": "%s", "filePath": "/pictures/x.png"}]}"""
                                .formatted(foodId, categoryId, absentId())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a PUT detaching a real picture answers 200 and removes it")
    void putDetachesPicture() throws Exception {
        UUID categoryId = createCategory("Losing");
        UUID foodId = createFood(categoryId, "Loses a picture", "3.00");
        UUID pictureId = attachPicture(foodId);

        mvc.perform(put("/api/v1/Foods/{id}", foodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "name": "Loses a picture", "description": "a dish",
                                 "price": 3.00, "currency": "USD", "categoryId": "%s",
                                 "deletedPictures": [{"id": "%s"}]}"""
                                .formatted(foodId, categoryId, pictureId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/FoodPictures/{id}", pictureId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown dish is 404 on every verb, where the original gave 500")
    void unknownFoodIsNotFound() throws Exception {
        UUID id = absentId();
        mvc.perform(get("/api/v1/Foods/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/Foods/{id}", id)).andExpect(status().isNotFound());
        // The dish is looked up before the body's category is, so this is a 404 rather than
        // the 400 an unknown category gets on a dish that does exist.
        mvc.perform(put("/api/v1/Foods/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "x", "price": 1.00, "categoryId": "%s"}"""
                                .formatted(absentId())))
                .andExpect(status().isNotFound());
    }

    // ----- pictures, currency, info --------------------------------------------------------

    @Test
    @DisplayName("an unknown picture is 404 on every verb")
    void unknownPictureIsNotFound() throws Exception {
        UUID id = absentId();
        mvc.perform(get("/api/v1/FoodPictures/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/FoodPictures/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("currencies list, and one can be looked up case insensitively")
    void currencies() throws Exception {
        mvc.perform(get("/api/v1/Currency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(162));
        mvc.perform(get("/api/v1/Currency/{code}", "usd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USD"));
        mvc.perform(get("/api/v1/Currency/{code}", "XYZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/Info answers in plain text")
    void info() throws Exception {
        mvc.perform(get("/Info"))
                .andExpect(status().isOk())
                .andExpect(content().string("Menu API"));
    }

    @Test
    @DisplayName("the root redirects at the Swagger UI")
    void rootRedirects() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui.html"));
    }

    @Test
    @DisplayName("a method the path does not have is a 405, not a 500")
    void methodNotAllowed() throws Exception {
        mvc.perform(delete("/api/v1/Currency"))
                .andExpect(status().isMethodNotAllowed());
    }
}
