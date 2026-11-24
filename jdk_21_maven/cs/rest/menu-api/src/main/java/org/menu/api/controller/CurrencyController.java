package org.menu.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.menu.api.dto.CurrencyDto;
import org.menu.api.dto.ErrorDto;
import org.menu.api.service.CurrencyProvider;
import org.menu.api.service.NotFoundException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/Currency", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Currency", description = "The ISO 4217 codes a dish may be priced in")
public class CurrencyController {

    private final CurrencyProvider provider;

    public CurrencyController(CurrencyProvider provider) {
        this.provider = provider;
    }

    @GetMapping
    @Operation(summary = "Every known currency")
    public List<CurrencyDto> getAll() {
        return provider.getAll();
    }

    @GetMapping("/{code}")
    @Operation(summary = "One currency by code",
            description = "Case insensitive, as the original lookup was. The original had this "
                    + "lookup but never exposed it.")
    @ApiResponse(responseCode = "200", description = "The currency")
    @ApiResponse(responseCode = "404", description = "No currency with that code",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CurrencyDto getByCode(@PathVariable String code) {
        CurrencyDto currency = provider.getByCode(code);
        if (currency == null) {
            throw new NotFoundException("no currency with code " + code);
        }
        return currency;
    }
}
