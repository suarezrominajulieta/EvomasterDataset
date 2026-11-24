package org.menu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared scaffolding: a booted application, and the handful of byte sequences the upload rules
 * are written against.
 *
 * <p>Every test makes the rows it needs through the API rather than leaning on the seed, so that
 * the order tests run in never matters.
 */
@SpringBootTest(properties = {
        "menu.pictures.path=${java.io.tmpdir}/menu-api-test-pictures",
        "spring.datasource.url=jdbc:h2:mem:menutest;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
public abstract class MenuApiTestBase {

    protected static final String JSON_PATCH = "application/json-patch+json";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    // ----- image bytes -------------------------------------------------------------------

    /** A PNG header followed by filler. Enough to clear the magic byte check. */
    protected static byte[] png() {
        return withHeader(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }

    protected static byte[] jpeg() {
        return withHeader(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    protected static byte[] gif() {
        return withHeader(new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61});
    }

    /** Bytes that are not any image at all, for the magic byte branch. */
    protected static byte[] notAnImage() {
        return "this is plainly not a picture".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withHeader(byte[] header) {
        byte[] out = new byte[header.length + 64];
        System.arraycopy(header, 0, out, 0, header.length);
        return out;
    }

    protected static MockMultipartFile pngPart(String partName, String fileName) {
        return new MockMultipartFile(partName, fileName, MediaType.IMAGE_PNG_VALUE, png());
    }

    protected static MockMultipartFile jpegPart(String partName, String fileName) {
        return new MockMultipartFile(partName, fileName, MediaType.IMAGE_JPEG_VALUE, jpeg());
    }

    /** A part that carries its own content type, the way an encoding block asks for. */
    protected static MockPart typedPart(String name, String content, String contentType) {
        MockPart part = new MockPart(name, content.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().set("Content-Type", contentType);
        return part;
    }

    // ----- fixtures ----------------------------------------------------------------------

    protected UUID createCategory(String name) throws Exception {
        String body = """
                {"name": "%s", "color": "White"}""".formatted(name);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/Categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).get("id").asText());
    }

    protected UUID createFood(UUID categoryId, String name, String price) throws Exception {
        String body = """
                {"name": "%s", "description": "a dish", "price": %s, "currency": "USD",
                 "categoryId": "%s"}""".formatted(name, price, categoryId);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/Foods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).get("id").asText());
    }

    /** A dish with one picture already attached, and the picture's id. */
    protected UUID attachPicture(UUID foodId) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .multipart("/api/v1/Foods/{id}/picture", foodId)
                        .file(pngPart("file", "dish.png")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).get("id").asText());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** A random id that names nothing. */
    protected static UUID absentId() {
        return UUID.randomUUID();
    }
}
