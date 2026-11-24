package org.menu.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // A query parameter can never override the Accept header.
        //
        // No fixed default is set. One would win over a client that asked for */*, and then
        // /Info, the only endpoint here that speaks text/plain, would answer 406 to a request
        // that named no preference at all.
        configurer.favorParameter(false)
                .ignoreAcceptHeader(false);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // The original redirected its root at the Swagger UI; so does this.
        registry.addRedirectViewController("/", "/swagger-ui.html");
    }
}
