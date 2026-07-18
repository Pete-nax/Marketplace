package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(Long id);

    boolean existsBySku(String sku);

    /**
     * Atomically decrements stock only if enough is available.
     * This single conditional UPDATE is what actually prevents overselling under
     * concurrent checkouts — two requests racing here will not both succeed,
     * because the WHERE clause is evaluated against the current committed row,
     * and the second UPDATE will affect 0 rows once stock is exhausted.
     *
     * Returns the number of rows updated: 1 = success, 0 = insufficient stock.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty " +
           "WHERE p.id = :productId AND p.stockQuantity >= :qty")
    int decrementStockIfAvailable(@Param("productId") Long productId, @Param("qty") Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :productId")
    void restock(@Param("productId") Long productId, @Param("qty") Integer qty);
}
