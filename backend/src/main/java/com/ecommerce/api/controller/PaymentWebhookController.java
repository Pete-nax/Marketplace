package com.ecommerce.api.controller;

import com.ecommerce.api.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    // Stripe posts raw JSON here; we must read the exact raw bytes (not a parsed DTO)
    // because the signature is computed over the exact payload string.
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String sigHeader = request.getHeader("Stripe-Signature");

        try {
            paymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("received");
        } catch (SecurityException e) {
            return ResponseEntity.status(400).body("invalid signature");
        }
    }
}
