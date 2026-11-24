package org.menu.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.menu.api.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/Foods/{id}/allergens}, and the reason it exists: to stand next to the PNG
 * icon endpoint as the half of a pair whose signature survives a body sent as text.
 */
class AllergenSheetTest extends MenuApiTestBase {

    @Autowired
    private FileStorageService storage;

    /**
     * A small but whole PDF. The second line is the binary marker real PDFs carry - four bytes
     * above 0x7F - which is exactly what a UTF-8 round trip mangles. The header and the trailer
     * around it are ASCII and come through untouched.
     */
    private static byte[] pdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n%".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[]{(byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3});
        out.writeBytes("\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n"
                .getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /** What arrives when a client renders the part as a String and sends it as UTF-8. */
    private static byte[] throughUtf8(byte[] raw) {
        return new String(raw, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.UTF_8);
    }

    private static MockMultipartFile sheet(String name, String type, byte[] bytes) {
        return new MockMultipartFile("sheet", name, type, bytes);
    }

    private UUID aDish() throws Exception {
        return createFood(createCategory("Allergenic " + UUID.randomUUID()), "Nutty", "8.00");
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            upload(UUID foodId) {
        return multipart("/api/v1/Foods/{id}/allergens", foodId);
    }

    // ----- what works ------------------------------------------------------------------------

    @Test
    @DisplayName("a whole PDF is stored and the dish points at it")
    void storesSheet() throws Exception {
        UUID foodId = aDish();
        mvc.perform(upload(foodId).file(sheet("allergens.pdf", "application/pdf", pdf())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(foodId.toString()))
                .andExpect(jsonPath("$.allergenSheetPath")
                        .value(org.hamcrest.Matchers.endsWith("allergens.pdf")));

        mvc.perform(get("/api/v1/Foods/{id}", foodId))
                .andExpect(jsonPath("$.allergenSheetPath").isNotEmpty());
    }

    @Test
    @DisplayName("the pair: a PDF survives being sent as UTF-8 text, a PNG does not")
    void textEncodedBodiesTellThemApart() throws Exception {
        // This is the property the endpoint is here for, written down as a test so it cannot
        // quietly stop being true. Both requests below are what a client produces when it
        // renders a binary part as a String: the PDF still passes, because its signature is
        // ASCII; the icon cannot, because a PNG begins with 0x89.
        UUID foodId = aDish();
        mvc.perform(upload(foodId)
                        .file(sheet("allergens.pdf", "application/pdf", throughUtf8(pdf()))))
                .andExpect(status().isOk());

        UUID categoryId = createCategory("Twin " + UUID.randomUUID());
        mvc.perform(multipart("/api/v1/Categories/{id}/icon", categoryId)
                        .file(new MockMultipartFile("icon", "icon.png", "image/png",
                                throughUtf8(png()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a trailer followed by a few newlines is still found")
    void trailerWithTrailingWhitespace() throws Exception {
        byte[] padded = (new String(pdf(), StandardCharsets.ISO_8859_1) + "\r\n\r\n\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        mvc.perform(upload(aDish()).file(sheet("allergens.pdf", "application/pdf", padded)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a second upload replaces the first, and the old file goes")
    void replaces() throws Exception {
        UUID foodId = aDish();
        MvcResult first = mvc.perform(upload(foodId)
                        .file(sheet("v1.pdf", "application/pdf", pdf())))
                .andExpect(status().isOk())
                .andReturn();
        String firstPath = read(first).get("allergenSheetPath").asText();
        String firstName = firstPath.substring("/pictures/".length());
        assertThat(storage.exists(firstName)).isTrue();

        mvc.perform(upload(foodId).file(sheet("v2.pdf", "application/pdf", pdf())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergenSheetPath")
                        .value(org.hamcrest.Matchers.endsWith("v2.pdf")));

        assertThat(storage.exists(firstName)).isFalse();
    }

    // ----- the rejections --------------------------------------------------------------------

    @Test
    @DisplayName("a dish that does not exist is 404")
    void absentDish() throws Exception {
        mvc.perform(upload(absentId()).file(sheet("a.pdf", "application/pdf", pdf())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a sheet part not sent as application/pdf is 415")
    void wrongContentType() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("a.pdf", "application/octet-stream", pdf())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    @DisplayName("a file that is not a .pdf is 415")
    void wrongExtension() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("allergens.txt", "application/pdf", pdf())))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("a double extension is 415, as for pictures")
    void doubleExtension() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("shell.jsp.pdf", "application/pdf", pdf())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("more than one extension")));
    }

    @Test
    @DisplayName("bytes that do not open with %PDF- are 422")
    void noHeader() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("a.pdf", "application/pdf", notAnImage())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("PDF header")));
    }

    @Test
    @DisplayName("a header with no %%EOF trailer is 422 too, and says which it was")
    void headerWithoutTrailer() throws Exception {
        byte[] truncated = "%PDF-1.4\n1 0 obj << >> endobj\n".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(upload(aDish()).file(sheet("a.pdf", "application/pdf", truncated)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("trailer")));
    }

    @Test
    @DisplayName("an empty sheet is 400")
    void empty() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("a.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a filename that climbs out is 403")
    void traversal() throws Exception {
        mvc.perform(upload(aDish()).file(sheet("../a.pdf", "application/pdf", pdf())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sheet part that arrived as a form field says so")
    void noFilename() throws Exception {
        mvc.perform(upload(aDish()).param("sheet", "%PDF-1.4 ... %%EOF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("without a filename")));
    }

    @Test
    @DisplayName("no sheet part at all is 400")
    void missing() throws Exception {
        mvc.perform(upload(aDish()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("allergenSheetPath is the server's: a patch that reaches for it is 403")
    void pathIsImmutable() throws Exception {
        mvc.perform(patch("/api/v1/Foods/{id}", aDish())
                        .contentType(JSON_PATCH)
                        .content("""
                                [{"op": "replace", "path": "/allergenSheetPath",
                                  "value": "/pictures/elsewhere.pdf"}]"""))
                .andExpect(status().isForbidden());
    }
}
