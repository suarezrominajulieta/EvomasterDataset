package org.movies.xml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * The published schema has to describe the XML shape exactly, because that schema is
 * the only thing a client has to go on. Jackson's XML annotations do not surface in
 * the generated document by themselves, so the xml metadata is attached here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI moviesXmlOpenApi() {
        return new OpenAPI().info(new Info()
                .title("movies-xml")
                .version("1.0.0")
                .description("An XML-only movie catalogue. Every request and response body is application/xml."));
    }

    @Bean
    public OpenApiCustomizer xmlMetadataCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();

            named(schemas, "MovieDto", "movie", schema -> {
                attribute(schema, "id");
                attribute(schema, "year");
                wrappedArray(schema, "cast", "cast", "actor");
            });
            named(schemas, "DirectorDto", "director", schema -> attribute(schema, "nationality"));
            named(schemas, "ActorDto", "actor", schema -> attribute(schema, "billing"));
            named(schemas, "MoviesDto", "movies", schema -> unwrappedArray(schema, "movies", "movie"));
            named(schemas, "StatsDto", "stats", schema -> {
                attribute(schema, "total");
                unwrappedArray(schema, "genres", "genre");
            });
            named(schemas, "GenreStatsDto", "genre", schema -> {
                attribute(schema, "name");
                attribute(schema, "count");
            });
            named(schemas, "ErrorDto", "error", schema -> attribute(schema, "status"));
        };
    }

    /** Names the root element of a schema and then lets the caller decorate its properties. */
    private static void named(Map<String, Schema> schemas,
                              String schemaName,
                              String elementName,
                              java.util.function.Consumer<Schema<?>> decorate) {
        Schema<?> schema = schemas.get(schemaName);
        if (schema == null) {
            return;
        }
        schema.setXml(new XML().name(elementName));
        decorate.accept(schema);
    }

    private static void attribute(Schema<?> parent, String property) {
        Schema<?> target = property(parent, property);
        if (target != null) {
            target.setXml(new XML().name(property).attribute(true));
        }
    }

    /** An array rendered as {@code <wrapper><item/></wrapper>}. */
    private static void wrappedArray(Schema<?> parent, String property, String wrapperName, String itemName) {
        Schema<?> array = property(parent, property);
        if (array == null) {
            return;
        }
        array.setXml(new XML().name(wrapperName).wrapped(true));
        if (array.getItems() != null) {
            array.getItems().setXml(new XML().name(itemName));
        }
    }

    /** An array whose items are direct children of the enclosing element. */
    private static void unwrappedArray(Schema<?> parent, String property, String itemName) {
        Schema<?> array = property(parent, property);
        if (array == null) {
            return;
        }
        array.setXml(new XML().name(itemName).wrapped(false));
        if (array.getItems() != null) {
            array.getItems().setXml(new XML().name(itemName));
        }
    }

    private static Schema<?> property(Schema<?> parent, String name) {
        Map<String, Schema> properties = parent.getProperties();
        return (properties == null) ? null : properties.get(name);
    }
}
