package com.ecommerce.api.service;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.enums.OrderStatus;
import com.ecommerce.api.enums.PaymentStatus;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final EmailService emailService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    /**
     * Entry point for Stripe's webhook POST. Two things matter here more than anything else:
     *
     * 1. Signature verification — without this, anyone who finds this URL could POST a
     *    fake "payment succeeded" event and get free orders marked PAID.
     * 2. Idempotency — Stripe retries webhook delivery on timeout/5xx, so the SAME event
     *    can arrive more than once. We store event.getId() with a unique DB constraint
     *    and no-op if we've already processed it.
     */
    public void handleWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            throw new SecurityException("Invalid webhook signature");
        }

        if (paymentRepository.existsByRawEventId(event.getId())) {
            log.info("Webhook event {} already processed, skipping", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    @Transactional
    protected void handlePaymentSucceeded(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException("Could not deserialize PaymentIntent from event"));

        Order order = orderRepository.findByStripePaymentIntentId(intent.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No order for payment intent: " + intent.getId()));

        // Guard against a delayed/duplicate event moving an already-final order backwards
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        Payment payment = Payment.builder()
                .order(order)
                .provider("STRIPE")
                .providerPaymentId(intent.getId())
                .status(PaymentStatus.SUCCEEDED)
                .amountCents(intent.getAmount())
                .rawEventId(event.getId())
                .build();
        paymentRepository.save(payment);

        emailService.sendOrderConfirmation(order);
    }

    @Transactional
    protected void handlePaymentFailed(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException("Could not deserialize PaymentIntent from event"));

        Order order = orderRepository.findByStripePaymentIntentId(intent.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No order for payment intent: " + intent.getId()));

        if (order.getStatus() == OrderStatus.PAID) {
            return; // don't downgrade an already-successful order
        }

        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);

        // Release the stock we reserved at checkout time back to inventory
        order.getItems().forEach(item ->
                productRepository.restock(item.getProduct().getId(), item.getQuantity()));

        Payment payment = Payment.builder()
                .order(order)
                .provider("STRIPE")
                .providerPaymentId(intent.getId())
                .status(PaymentStatus.FAILED)
                .amountCents(intent.getAmount())
                .rawEventId(event.getId())
                .build();
        paymentRepository.save(payment);
    }
}
