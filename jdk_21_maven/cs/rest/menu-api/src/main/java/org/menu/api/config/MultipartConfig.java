package org.menu.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * The limits themselves live in application.properties, set high on purpose: every refusal
 * should come from {@code FileValidationService}, where it is a visible branch with a status
 * code of its own, rather than from the container, where it is one opaque error for everything.
 */
@Configuration
public class MultipartConfig {

    @Bean
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        // Parts are read up front. Lazy resolution would postpone a malformed body until
        // whichever handler first touched a part, which makes the same request answer
        // differently depending on the endpoint it was aimed at.
        resolver.setResolveLazily(false);
        return resolver;
    }
}
