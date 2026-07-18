package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the actual claim made in the README: that decrementStockIfAvailable
 * prevents overselling under real concurrent access, not just in theory.
 *
 * Runs against a real Postgres container (not H2) because the schema uses
 * Postgres-specific SQL — see V1__init_schema.sql.
 *
 * Propagation.NOT_SUPPORTED disables @DataJpaTest's default "wrap every test in
 * a transaction and roll it back" behavior. That default would keep the seeded
 * row uncommitted and invisible to the separate DB connections each worker
 * thread opens below — this test needs real, committed, cross-connection state.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductStockConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long lastUnitProductId;

    @BeforeEach
    void seedProductWithExactlyOneUnitInStock() {
        Category category = categoryRepository.save(
                Category.builder().name("Laptops-" + System.nanoTime()).slug("laptops-" + System.nanoTime()).build());

        Product product = productRepository.save(Product.builder()
                .category(category)
                .name("Last Laptop In Stock")
                .sku("SKU-" + System.nanoTime())
                .priceCents(150000L)
                .stockQuantity(1)
                .active(true)
                .build());

        lastUnitProductId = product.getId();
    }

    @Test
    void tenConcurrentBuyers_onlyOneSucceedsForTheLastUnit() throws InterruptedException {
        int buyerCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(buyerCount);
        CountDownLatch startGate = new CountDownLatch(1);   // releases all threads at once
        CountDownLatch doneGate = new CountDownLatch(buyerCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < buyerCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    int rowsUpdated = productRepository.decrementStockIfAvailable(lastUnitProductId, 1);
                    if (rowsUpdated == 1) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // fire all 10 requests at the same instant
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("all threads should complete without deadlock").isTrue();

        // This is the assertion that matters: exactly one buyer got the last unit,
        // no matter how many raced for it at the same time.
        assertThat(successCount.get()).isEqualTo(1);

        Product finalState = productRepository.findById(lastUnitProductId).orElseThrow();
        assertThat(finalState.getStockQuantity()).isEqualTo(0);
    }

    @Test
    void decrementStockIfAvailable_returnsZeroRowsWhenRequestExceedsStock() {
        int rowsUpdated = productRepository.decrementStockIfAvailable(lastUnitProductId, 2);

        assertThat(rowsUpdated).isEqualTo(0);
        Product unchanged = productRepository.findById(lastUnitProductId).orElseThrow();
        assertThat(unchanged.getStockQuantity()).isEqualTo(1); // stock untouched on failed decrement
    }

    @Test
    void decrementStockIfAvailable_succeedsAndPersistsWhenStockSufficient() {
        int rowsUpdated = productRepository.decrementStockIfAvailable(lastUnitProductId, 1);

        assertThat(rowsUpdated).isEqualTo(1);
        Product updated = productRepository.findById(lastUnitProductId).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(0);
    }
}
