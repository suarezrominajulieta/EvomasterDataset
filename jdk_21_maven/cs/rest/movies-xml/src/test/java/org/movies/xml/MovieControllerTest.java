package org.movies.xml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovieControllerTest {

    private static final String MATRIX = """
            <movie year="1999">
                <title>The Matrix</title>
                <genre>SCIFI</genre>
                <rating>8.7</rating>
                <director nationality="US"><name>Lana Wachowski</name></director>
                <cast>
                    <actor billing="1"><name>Keanu Reeves</name></actor>
                    <actor billing="2"><name>Laurence Fishburne</name></actor>
                </cast>
            </movie>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper jsonMapper;

    // ------------------------------------------------------------- creation

    @Test
    void validXmlCreatesTheMovie() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content(MATRIX))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(header().exists("Location"))
                .andExpect(xpath("/movie/@id").exists())
                .andExpect(xpath("/movie/@year").string("1999"))
                .andExpect(xpath("/movie/title").string("The Matrix"))
                .andExpect(xpath("/movie/director/@nationality").string("US"))
                .andExpect(xpath("/movie/cast/actor[1]/@billing").string("1"))
                .andExpect(xpath("/movie/cast/actor[2]/name").string("Laurence Fishburne"));
    }

    @Test
    void locationHeaderPointsAtTheCreatedMovie() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/title").string("The Matrix"));
    }

    @Test
    void jsonBodyIsRejectedWithUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"The Matrix\",\"year\":1999,\"genre\":\"SCIFI\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedXmlIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML)
                        .content("<movie year=\"1999\"><title>The Matrix</movie>"))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("/error/@status").string("400"))
                .andExpect(xpath("/error/message").exists());
    }

    @Test
    void idAndYearSpelledAsElementsAreRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie>
                            <id>7</id>
                            <year>1999</year>
                            <title>The Matrix</title>
                            <genre>SCIFI</genre>
                            <director><name>Lana Wachowski</name></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("/error/@status").string("400"));
    }

    @Test
    void unwrappedCastIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>The Matrix</title>
                            <genre>SCIFI</genre>
                            <director><name>Lana Wachowski</name></director>
                            <actor billing="1"><name>Keanu Reeves</name></actor>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nationalitySpelledAsAnElementIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>The Matrix</title>
                            <genre>SCIFI</genre>
                            <director><name>Lana Wachowski</name><nationality>US</nationality></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateTitleAndYearAreRejected() throws Exception {
        create(MATRIX);

        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>   the   MATRIX  </title>
                            <genre>ACTION</genre>
                            <director><name>Someone Else</name></director>
                        </movie>
                        """))
                .andExpect(status().isConflict())
                .andExpect(xpath("/error/@status").string("409"));
    }

    @Test
    void sameTitleInAnotherYearIsAccepted() throws Exception {
        create(MATRIX);

        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="2003">
                            <title>The Matrix</title>
                            <genre>SCIFI</genre>
                            <director><name>Lana Wachowski</name></director>
                        </movie>
                        """))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------- business rules

    @Test
    void repeatedBillingIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>Twice Billed</title>
                            <genre>DRAMA</genre>
                            <director><name>Someone</name></director>
                            <cast>
                                <actor billing="1"><name>A</name></actor>
                                <actor billing="1"><name>B</name></actor>
                            </cast>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aHighRatingWithoutCastIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>Acclaimed But Empty</title>
                            <genre>DRAMA</genre>
                            <rating>9.5</rating>
                            <director><name>Someone</name></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aHighRatingWithCastIsAccepted() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>Acclaimed</title>
                            <genre>DRAMA</genre>
                            <rating>9.5</rating>
                            <director><name>Someone</name></director>
                            <cast><actor billing="1"><name>A</name></actor></cast>
                        </movie>
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void aLowercaseNationalityIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>Bad Nationality</title>
                            <genre>DRAMA</genre>
                            <director nationality="us"><name>Someone</name></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOutOfRangeYearIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1500">
                            <title>Too Old</title>
                            <genre>DRAMA</genre>
                            <director><name>Someone</name></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("/error/message").exists());
    }

    @Test
    void anUnknownGenreIsRejected() throws Exception {
        mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="1999">
                            <title>Unknown Genre</title>
                            <genre>MUSICAL</genre>
                            <director><name>Someone</name></director>
                        </movie>
                        """))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- reads

    @Test
    void anEmptyCatalogueIsAnEmptyMoviesElement() throws Exception {
        mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/movies/movie").doesNotExist());
    }

    @Test
    void anUnknownIdIsReportedAsAnXmlError() throws Exception {
        mockMvc.perform(get("/movies/424242"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/error/@status").string("404"))
                .andExpect(xpath("/error/message").exists());
    }

    @Test
    void searchFiltersAndSorts() throws Exception {
        create(MATRIX);
        create("""
                <movie year="2001">
                    <title>Amelie</title>
                    <genre>COMEDY</genre>
                    <rating>7.2</rating>
                    <director nationality="FR"><name>Jean-Pierre Jeunet</name></director>
                </movie>
                """);
        create("""
                <movie year="2010">
                    <title>Unrated Drama</title>
                    <genre>DRAMA</genre>
                    <director><name>Nobody</name></director>
                </movie>
                """);

        mockMvc.perform(get("/movies").param("genre", "SCIFI"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movies/movie").nodeCount(1))
                .andExpect(xpath("/movies/movie[1]/title").string("The Matrix"));

        mockMvc.perform(get("/movies").param("titleContains", "mat"))
                .andExpect(xpath("/movies/movie").nodeCount(1));

        mockMvc.perform(get("/movies").param("minYear", "2000").param("maxYear", "2005"))
                .andExpect(xpath("/movies/movie").nodeCount(1))
                .andExpect(xpath("/movies/movie[1]/title").string("Amelie"));

        mockMvc.perform(get("/movies").param("minRating", "7.0"))
                .andExpect(xpath("/movies/movie").nodeCount(2));

        mockMvc.perform(get("/movies").param("sort", "title"))
                .andExpect(xpath("/movies/movie[1]/title").string("Amelie"));

        mockMvc.perform(get("/movies").param("sort", "year"))
                .andExpect(xpath("/movies/movie[1]/title").string("The Matrix"));
    }

    @Test
    void invalidSearchParametersAreRejected() throws Exception {
        mockMvc.perform(get("/movies").param("genre", "MUSICAL"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/movies").param("minYear", "2000").param("maxYear", "1990"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/movies").param("sort", "director"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsSkipEmptyGenresAndAverageOnlyRatedMovies() throws Exception {
        create(MATRIX);
        create("""
                <movie year="2010">
                    <title>Unrated Drama</title>
                    <genre>DRAMA</genre>
                    <director><name>Nobody</name></director>
                </movie>
                """);

        mockMvc.perform(get("/movies/stats"))
                .andExpect(status().isOk())
                .andExpect(xpath("/stats/@total").string("2"))
                .andExpect(xpath("/stats/genre").nodeCount(2))
                .andExpect(xpath("/stats/genre[@name='SCIFI']/averageRating").string("8.7"))
                .andExpect(xpath("/stats/genre[@name='DRAMA']/averageRating").doesNotExist())
                .andExpect(xpath("/stats/genre[@name='HORROR']").doesNotExist());
    }

    // -------------------------------------------------------------- updates

    @Test
    void putReplacesEveryField() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_XML).content("""
                        <movie year="2003">
                            <title>The Matrix Reloaded</title>
                            <genre>ACTION</genre>
                            <director nationality="AU"><name>Lilly Wachowski</name></director>
                        </movie>
                        """))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/@year").string("2003"))
                .andExpect(xpath("/movie/title").string("The Matrix Reloaded"))
                .andExpect(xpath("/movie/genre").string("ACTION"))
                .andExpect(xpath("/movie/rating").doesNotExist())
                .andExpect(xpath("/movie/cast/actor").doesNotExist());
    }

    @Test
    void putOnAnUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(put("/movies/424242").contentType(MediaType.APPLICATION_XML).content(MATRIX))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchTouchesOnlyTheTitle() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><title>Matrix, The</title></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/title").string("Matrix, The"))
                .andExpect(xpath("/movie/@year").string("1999"))
                .andExpect(xpath("/movie/genre").string("SCIFI"))
                .andExpect(xpath("/movie/rating").string("8.7"))
                .andExpect(xpath("/movie/cast/actor").nodeCount(2));
    }

    @Test
    void patchTouchesOnlyTheYear() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie year=\"2000\"/>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/@year").string("2000"))
                .andExpect(xpath("/movie/title").string("The Matrix"));
    }

    @Test
    void patchTouchesOnlyTheGenre() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><genre>ACTION</genre></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/genre").string("ACTION"))
                .andExpect(xpath("/movie/rating").string("8.7"));
    }

    @Test
    void patchTouchesOnlyTheRating() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><rating>9.9</rating></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/rating").string("9.9"))
                .andExpect(xpath("/movie/title").string("The Matrix"));
    }

    @Test
    void patchTouchesOnlyTheDirector() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><director nationality=\"AU\"><name>Lilly Wachowski</name></director></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/director/name").string("Lilly Wachowski"))
                .andExpect(xpath("/movie/director/@nationality").string("AU"))
                .andExpect(xpath("/movie/title").string("The Matrix"));
    }

    @Test
    void patchWithAnEmptyCastClearsIt() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><cast></cast></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/cast/actor").doesNotExist())
                .andExpect(xpath("/movie/title").string("The Matrix"));
    }

    @Test
    void patchWithoutACastLeavesItIntact() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><genre>ACTION</genre></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/cast/actor").nodeCount(2));
    }

    @Test
    void patchReplacesTheWholeCast() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><cast><actor billing=\"1\"><name>Carrie-Anne Moss</name></actor></cast></movie>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/movie/cast/actor").nodeCount(1))
                .andExpect(xpath("/movie/cast/actor[1]/name").string("Carrie-Anne Moss"));
    }

    @Test
    void patchThatWouldLeaveAHighRatingWithoutCastIsRejected() throws Exception {
        String location = create("""
                <movie year="1999">
                    <title>Acclaimed</title>
                    <genre>DRAMA</genre>
                    <rating>9.5</rating>
                    <director><name>Someone</name></director>
                    <cast><actor billing="1"><name>A</name></actor></cast>
                </movie>
                """);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie><cast></cast></movie>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchIntoADuplicateIsRejected() throws Exception {
        create(MATRIX);
        String location = create("""
                <movie year="2003">
                    <title>The Matrix Reloaded</title>
                    <genre>ACTION</genre>
                    <director><name>Lana Wachowski</name></director>
                </movie>
                """);

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_XML)
                        .content("<movie year=\"1999\"><title>The Matrix</title></movie>"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchOnAnUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(patch("/movies/424242").contentType(MediaType.APPLICATION_XML)
                        .content("<movie><genre>ACTION</genre></movie>"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- deletion

    @Test
    void deleteRemovesTheMovie() throws Exception {
        String location = create(MATRIX);

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void deleteOnAnUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(delete("/movies/424242"))
                .andExpect(status().isNotFound())
                .andExpect(xpath("/error/@status").string("404"));
    }

    // ---------------------------------------------------------- the schema

    @Test
    void theOpenApiSchemaIsJsonAndNeverOffersJsonBodies() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = jsonMapper.readTree(result.getResponse().getContentAsString());

        JsonNode paths = document.path("paths");
        assertThat(paths.isMissingNode()).isFalse();
        paths.forEach(path -> path.forEach(operation -> {
            assertOnlyXml(operation.path("requestBody").path("content"));
            operation.path("responses").forEach(response -> assertOnlyXml(response.path("content")));
        }));

        JsonNode movie = document.path("components").path("schemas").path("MovieDto");
        assertThat(movie.path("xml").path("name").asText()).isEqualTo("movie");
        assertThat(movie.path("properties").path("id").path("xml").path("attribute").asBoolean()).isTrue();
        assertThat(movie.path("properties").path("year").path("xml").path("attribute").asBoolean()).isTrue();
        assertThat(movie.path("properties").path("cast").path("xml").path("wrapped").asBoolean()).isTrue();
        assertThat(movie.path("properties").path("cast").path("items").path("xml").path("name").asText())
                .isEqualTo("actor");

        JsonNode director = document.path("components").path("schemas").path("DirectorDto");
        assertThat(director.path("properties").path("nationality").path("xml").path("attribute").asBoolean()).isTrue();
    }

    private static void assertOnlyXml(JsonNode content) {
        if (content.isMissingNode()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> mediaTypes = content.fields();
        while (mediaTypes.hasNext()) {
            assertThat(mediaTypes.next().getKey()).isEqualTo(MediaType.APPLICATION_XML_VALUE);
        }
    }

    // --------------------------------------------------------------- tools

    private String create(String xml) throws Exception {
        MvcResult result = mockMvc.perform(post("/movies").contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }
}
