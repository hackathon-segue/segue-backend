package com.segue.backend.repository;

import com.segue.backend.domain.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findBySkuId(Long skuId);
}
