package com.ecommerce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String shippingAddress
) {}
