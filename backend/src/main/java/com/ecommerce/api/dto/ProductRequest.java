package com.ecommerce.api.dto;

import jakarta.validation.constraints.*;

public record ProductRequest(
        @NotBlank String name,
        String description,
        @NotBlank String sku,
        @NotNull @Positive Long categoryId,
        @NotNull @PositiveOrZero Long priceCents,
        @NotNull @PositiveOrZero Integer stockQuantity,
        String imageUrl
) {}
