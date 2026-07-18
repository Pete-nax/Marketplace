package com.ecommerce.api.service;

import com.ecommerce.api.dto.*;
import com.ecommerce.api.entity.*;
import com.ecommerce.api.enums.OrderStatus;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.*;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Value("${stripe.currency}")
    private String currency;

    /**
     * Checkout flow:
     *  1. Read the user's cart.
     *  2. Inside a single DB transaction, attempt to atomically decrement stock for every
     *     line item. If ANY item is out of stock, the whole transaction rolls back —
     *     nobody gets charged for a partially-fulfillable order.
     *  3. Create the Order + OrderItems (with price snapshotted at this moment).
     *  4. Create a Stripe PaymentIntent for the order total and return its client secret.
     *  5. Clear the cart.
     *
     * The order is created as PENDING. It only becomes PAID when the Stripe webhook
     * confirms payment_intent.succeeded — see PaymentService. If payment never completes,
     * a scheduled job (not included here) should release stock for stale PENDING orders.
     */
    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest request) {
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        Order order = Order.builder()
                .user(userRepository.getReferenceById(userId))
                .status(OrderStatus.PENDING)
                .currency(currency)
                .shippingAddress(request.shippingAddress())
                .totalAmountCents(0L)
                .build();

        long total = 0L;
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            // Atomic, race-safe decrement. If two users check out the last unit
            // simultaneously, only one of these UPDATEs will affect a row.
            int updated = productRepository.decrementStockIfAvailable(product.getId(), cartItem.getQuantity());
            if (updated == 0) {
                throw new InsufficientStockException(
                        "Not enough stock for \"" + product.getName() + "\" — only " +
                        product.getStockQuantity() + " left, requested " + cartItem.getQuantity());
            }

            long lineTotal = product.getPriceCents() * cartItem.getQuantity();
            total += lineTotal;

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .unitPriceCents(product.getPriceCents())
                    .quantity(cartItem.getQuantity())
                    .build();
            order.getItems().add(orderItem);
        }

        order.setTotalAmountCents(total);
        orderRepository.save(order);

        PaymentIntent intent = createPaymentIntent(order, total);
        order.setStripePaymentIntentId(intent.getId());

        cartItemRepository.deleteByUserId(userId);

        return new CheckoutResponse(order.getId(), intent.getClientSecret(), total, currency);
    }

    // Returns the full PaymentIntent (not just the client secret) because the caller
    // needs the intent id too. Never stash this in an instance field — this service is
    // a singleton bean shared across every concurrent request.
    private PaymentIntent createPaymentIntent(Order order, long amountCents) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(currency)
                    .putMetadata("order_id", String.valueOf(order.getId()))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();
            return PaymentIntent.create(params);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    public List<OrderResponse> getOrdersForUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return toResponse(order);
    }

    public List<OrderResponse> getAllOrdersForAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProduct().getId(), i.getProductNameSnapshot(), i.getUnitPriceCents(), i.getQuantity()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotalAmountCents(),
                order.getCurrency(), order.getShippingAddress(), items, order.getCreatedAt());
    }
}
