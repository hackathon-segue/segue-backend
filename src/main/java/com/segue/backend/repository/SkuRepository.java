package com.segue.backend.repository;

import com.segue.backend.domain.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findByProductId(Long productId);

    Optional<Sku> findByProductIdAndColorAndSize(Long productId, String color, String size);
}
