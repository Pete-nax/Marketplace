package com.ecommerce.api.service;

import com.ecommerce.api.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOrderConfirmation(Order order) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(order.getUser().getEmail());
            message.setSubject("Order Confirmed — #" + order.getId());
            message.setText("Thanks for your order! Total: " +
                    formatCents(order.getTotalAmountCents(), order.getCurrency()) +
                    "\nWe'll notify you when it ships.");
            mailSender.send(message);
        } catch (Exception e) {
            // Email failure should never roll back a paid order — log and move on.
            log.error("Failed to send order confirmation email for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private String formatCents(long cents, String currency) {
        return String.format("%.2f %s", cents / 100.0, currency.toUpperCase());
    }
}
