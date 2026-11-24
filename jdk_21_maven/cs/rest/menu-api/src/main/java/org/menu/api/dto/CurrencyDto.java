package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An ISO 4217 currency")
public record CurrencyDto(
        @Schema(example = "USD") String code,
        @Schema(example = "United States Dollar") String name) {
}
