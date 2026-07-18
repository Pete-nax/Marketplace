package com.ecommerce.api.controller;

import com.ecommerce.api.dto.CartItemRequest;
import com.ecommerce.api.dto.CartItemResponse;
import com.ecommerce.api.security.CustomUserDetails;
import com.ecommerce.api.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCart(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addOrUpdateItem(@AuthenticationPrincipal CustomUserDetails user,
                                                              @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addOrUpdateItem(user.getId(), request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long itemId) {
        cartService.removeItem(user.getId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal CustomUserDetails user) {
        cartService.clearCart(user.getId());
        return ResponseEntity.noContent().build();
    }
}
