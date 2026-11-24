package org.menu.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A restaurant menu API, ported loosely from the .NET Menu.API of the EvoMaster benchmark.
 *
 * <p>It exists to be fuzzed. The interesting surface is multipart/form-data uploads
 * (see the controllers under {@code /api/v1/Foods} and {@code /api/v1/Categories}) and
 * JSON Patch, both of which fan out into many distinguishable response codes on purpose.
 */
@SpringBootApplication
public class MenuApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuApiApplication.class, args);
    }
}
