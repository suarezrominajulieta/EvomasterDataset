package org.menu.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published schema is what a black box client has to go on, so it is worth being exact about.
 *
 * <p>In particular the multipart bodies are declared by hand on each controller method, with an
 * explicit {@code encoding} block where a part carries a content type of its own: springdoc will
 * not infer one from bare {@code @RequestPart} parameters, and that block is precisely what a
 * client needs in order to build the body correctly.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI menuApiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("menu-api")
                .version("1.0.0")
                .description("""
                        A restaurant menu API, loosely ported from the .NET Menu.API of the \
                        EvoMaster benchmark. It exists to be fuzzed: the multipart upload \
                        endpoints and the JSON Patch endpoints both fan out into many \
                        distinguishable status codes on purpose."""));
    }

    /**
     * Two answers come from the exception handler rather than from any one endpoint, so no
     * annotation on a controller would ever mention them, and every operation can produce both.
     *
     * <p>The handler also lets anything unexpected surface as a 500, and that one is deliberately
     * left undeclared: this is a fuzzing target, and a 500 is a fault to be reported, not a
     * documented answer a tool should accept.
     */
    @Bean
    public OpenApiCustomizer handlerWideResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (PathItem path : openApi.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    ApiResponses responses = operation.getResponses();
                    if (responses == null) {
                        continue;
                    }
                    errorResponse(responses, "405", "Method not supported on this path");
                    errorResponse(responses, "415", "Body sent in a media type this path does not take");
                }
            }
        };
    }

    /** Adds one error response, leaving an explicitly annotated one alone. */
    private static void errorResponse(ApiResponses responses, String code, String description) {
        if (responses.containsKey(code)) {
            return;
        }
        responses.addApiResponse(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ErrorDto")))));
    }
}
