package com.ecommerce.api.dto;

public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPriceCents,
        Integer quantity
) {}
