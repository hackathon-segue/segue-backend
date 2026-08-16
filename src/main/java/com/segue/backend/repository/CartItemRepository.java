package com.segue.backend.repository;

import com.segue.backend.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCustomerIdOrderBySavedAtDesc(Long customerId);
}
