package com.segue.backend.controller;

import com.segue.backend.dto.request.CartAddRequest;
import com.segue.backend.dto.response.CartItemResponse;
import com.segue.backend.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** F0: 장바구니 저장 (고객 모바일), F2: 장바구니 및 재고 확인 (태블릿) */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse addToCart(@Valid @RequestBody CartAddRequest request) {
        return cartService.addToCart(request);
    }

    @GetMapping
    public List<CartItemResponse> getCart(@RequestParam Long customerId,
                                           @RequestParam(required = false) Long storeId) {
        return cartService.getCart(customerId, storeId);
    }
}
