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

    /**
     * F2: CA 가 태블릿에서 고객 장바구니를 조회한다. 남의 데이터를 보는 것이므로 동의 게이트가 걸린다
     * (기능명세서 5번). 동의가 없으면 403.
     */
    @GetMapping
    public List<CartItemResponse> getCart(@RequestParam Long customerId,
                                           @RequestParam(required = false) Long storeId) {
        return cartService.getCart(customerId, storeId);
    }

    /**
     * 고객이 자기 쇼핑백을 본다. 본인 데이터를 본인이 보는 것이라 동의 게이트를 걸지 않는다.
     *
     * 동의 게이트는 "CA 가 고객 데이터를 열람할 때 동의를 확인한다"는 취지이므로(기능명세서 5번),
     * 고객 본인 조회까지 막는 것은 의도가 아니다. 위 CA 경로와 엔드포인트를 분리해 두어야
     * 파라미터 실수로 게이트가 조용히 우회되는 일이 없다.
     */
    @GetMapping("/mine")
    public List<CartItemResponse> getMyCart(@RequestParam Long customerId,
                                             @RequestParam(required = false) Long storeId) {
        return cartService.getOwnCart(customerId, storeId);
    }
}
