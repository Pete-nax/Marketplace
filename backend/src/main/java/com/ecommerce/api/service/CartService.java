package com.ecommerce.api.service;

import com.ecommerce.api.dto.CartItemRequest;
import com.ecommerce.api.dto.CartItemResponse;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CartItemRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public List<CartItemResponse> getCart(Long userId) {
        return cartItemRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CartItemResponse addOrUpdateItem(Long userId, CartItemRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));

        // Note: this check is advisory only, to give the user fast feedback.
        // The authoritative check happens atomically at checkout time via decrementStockIfAvailable,
        // since stock can change between now and then.
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, request.productId())
                .orElseGet(() -> CartItem.builder()
                        .user(userRepository.getReferenceById(userId))
                        .product(product)
                        .quantity(0)
                        .build());

        item.setQuantity(request.quantity());
        return toResponse(cartItemRepository.save(item));
    }

    @Transactional
    public void removeItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        if (!item.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found: " + cartItemId); // don't leak existence to other users
        }
        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    private CartItemResponse toResponse(CartItem item) {
        Product p = item.getProduct();
        return new CartItemResponse(
                item.getId(), p.getId(), p.getName(), p.getPriceCents(),
                item.getQuantity(), p.getPriceCents() * item.getQuantity(), p.getStockQuantity());
    }
}
