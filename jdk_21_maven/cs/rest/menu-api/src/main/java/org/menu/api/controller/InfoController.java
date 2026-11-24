package org.menu.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Info", description = "Liveness, in the plainest form there is")
public class InfoController {

    @GetMapping(path = "/Info", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "The name of this API, as plain text")
    public String index() {
        return "Menu API";
    }
}
