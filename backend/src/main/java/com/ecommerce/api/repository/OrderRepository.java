package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<Order> findAllByOrderByCreatedAtDesc(); // admin: all orders
}
