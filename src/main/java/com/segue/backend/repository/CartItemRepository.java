package com.segue.backend.repository;

import com.segue.backend.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCustomerIdOrderBySavedAtDesc(Long customerId);

    /**
     * 이 수정 이전에 쌓인 중복 행이 남아 있는 DB 에서도 동작해야 하므로 단건 조회(Optional)가 아니라
     * 가장 최근 항목 하나를 집는다. 단건으로 조회하면 기존 중복 때문에 500 이 발생한다.
     */
    Optional<CartItem> findFirstByCustomerIdAndSkuIdOrderBySavedAtDesc(Long customerId, Long skuId);
}
