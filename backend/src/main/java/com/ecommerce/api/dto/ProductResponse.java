package com.ecommerce.api.dto;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String sku,
        Long categoryId,
        String categoryName,
        Long priceCents,
        Integer stockQuantity,
        String imageUrl,
        Boolean active
) {}
