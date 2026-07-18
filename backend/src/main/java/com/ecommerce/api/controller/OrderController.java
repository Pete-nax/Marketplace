package com.ecommerce.api.controller;

import com.ecommerce.api.dto.CheckoutRequest;
import com.ecommerce.api.dto.CheckoutResponse;
import com.ecommerce.api.dto.OrderResponse;
import com.ecommerce.api.security.CustomUserDetails;
import com.ecommerce.api.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@AuthenticationPrincipal CustomUserDetails user,
                                                       @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(orderService.checkout(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> myOrders(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(orderService.getOrdersForUser(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(user.getId(), id));
    }
}
