package com.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        Long totalAmountCents,
        String currency,
        String shippingAddress,
        List<OrderItemResponse> items,
        Instant createdAt
) {}
