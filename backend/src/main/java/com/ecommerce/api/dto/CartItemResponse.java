package com.ecommerce.api.dto;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        Long unitPriceCents,
        Integer quantity,
        Long lineTotalCents,
        Integer availableStock
) {}
