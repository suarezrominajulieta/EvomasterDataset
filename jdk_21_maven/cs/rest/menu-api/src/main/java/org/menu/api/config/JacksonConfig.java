package org.menu.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper settings, written out here rather than in application.properties so that each one can
 * say why it is the way it is.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer menuJacksonCustomizer() {
        return builder -> {
            // The .NET original took a property under any capitalisation, and a client written
            // against it would send "Name" as readily as "name". That stays true here.
            builder.featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);

            // An unknown field is ignored rather than refused, again matching the original.
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // Nulls are serialised. A response that simply omits a field leaves a client
            // guessing whether it is absent or null, and a JSON Patch pointing at an absent
            // field is a 409 here, so the difference is one clients can observe.
            builder.serializationInclusion(JsonInclude.Include.ALWAYS);
        };
    }
}
