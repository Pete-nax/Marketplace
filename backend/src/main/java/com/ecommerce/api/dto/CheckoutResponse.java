package com.ecommerce.api.dto;

public record CheckoutResponse(
        Long orderId,
        String clientSecret,   // Stripe PaymentIntent client secret, used by frontend to confirm payment
        Long totalAmountCents,
        String currency
) {}
