package com.segue.backend.service;

import com.segue.backend.domain.CartItem;
import com.segue.backend.domain.Customer;
import com.segue.backend.domain.Inventory;
import com.segue.backend.domain.Sku;
import com.segue.backend.dto.request.CartAddRequest;
import com.segue.backend.dto.response.CartItemResponse;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.repository.CartItemRepository;
import com.segue.backend.repository.InventoryRepository;
import com.segue.backend.repository.ProductRepository;
import com.segue.backend.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** F0: 장바구니 저장, F2: 장바구니 및 SKU 기준 재고 확인 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final SkuRepository skuRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;

    @Transactional
    public CartItemResponse addToCart(CartAddRequest request) {
        Customer customer = customerService.getById(request.getCustomerId());
        Sku sku = skuRepository.findByProductIdAndColorAndSize(
                        request.getProductId(), request.getColor(), request.getSize())
                .orElseThrow(() -> buildSkuNotFoundException(request.getProductId(), request.getColor(), request.getSize()));

        // F2 는 장바구니를 "최근 담은 순"으로 보여주고 항목마다 상담 시작 버튼을 배치한다.
        // 같은 SKU 를 다시 담았을 때 행을 늘리면 태블릿에 동일한 제품이 여러 줄로 뜨고
        // "Last Intent 시작" 버튼도 중복되므로, 새 행을 만들지 않고 담은 시각만 갱신한다.
        CartItem saved = cartItemRepository
                .findFirstByCustomerIdAndSkuIdOrderBySavedAtDesc(customer.getId(), sku.getId())
                .map(existing -> {
                    existing.setSavedAt(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> cartItemRepository.save(CartItem.builder()
                        .customer(customer)
                        .sku(sku)
                        .color(request.getColor())
                        .size(request.getSize())
                        .savedAt(LocalDateTime.now())
                        .build()));

        // 신규 저장 시점에는 재고 조회 없이도 표시 가능하므로 store 문맥 없이 기본 응답을 만든다.
        return toResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> getCart(Long customerId, Long storeId) {
        customerService.getById(customerId); // 존재 검증
        // 기능명세서 5번: CA의 회원 장바구니 조회는 고객 동의가 확인된 경우에만 허용한다.
        customerService.requireConsent(customerId);
        return cartItemRepository.findByCustomerIdOrderBySavedAtDesc(customerId).stream()
                .map(item -> toResponse(item, storeId))
                .toList();
    }

    private CartItemResponse toResponse(CartItem item, Long storeId) {
        Sku sku = item.getSku();
        Optional<Inventory> inventory = storeId == null
                ? Optional.empty()
                : inventoryRepository.findBySkuIdAndStoreId(sku.getId(), storeId);

        boolean currentStoreInStock = inventory.map(Inventory::getCurrentStoreInStock).orElse(false);
        boolean otherStoreInStock = inventory.map(Inventory::getOtherStoreInStock).orElse(false);
        boolean restockPlanned = inventory.map(Inventory::getRestockPlanned).orElse(false);

        String actionButtonLabel = currentStoreInStock ? "제품 확인하기" : "Last Intent 시작";

        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(sku.getProduct().getId())
                .productName(sku.getProduct().getName())
                .imageUrl(sku.getProduct().getImageUrl())
                .category(sku.getProduct().getCategory())
                .skuId(sku.getId())
                .color(item.getColor())
                .size(item.getSize())
                .currentStoreInStock(currentStoreInStock)
                .otherStoreInStock(otherStoreInStock)
                .restockPlanned(restockPlanned)
                .actionButtonLabel(actionButtonLabel)
                .savedAt(item.getSavedAt())
                .build();
    }

    private NotFoundException buildSkuNotFoundException(Long productId, String color, String size) {
        if (!productRepository.existsById(productId)) {
            return new NotFoundException("제품을 찾을 수 없습니다. productId=" + productId);
        }
        List<Sku> availableSkus = skuRepository.findByProductId(productId);
        String options = availableSkus.stream()
                .map(s -> s.getColor() + "/" + s.getSize())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        return new NotFoundException(
                "선택한 컬러/사이즈 조합(" + color + "/" + size + ")은 존재하지 않습니다. 선택 가능한 옵션: " + options);
    }
}
