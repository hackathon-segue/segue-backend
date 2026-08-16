package com.segue.backend.controller;

import com.segue.backend.dto.request.ConsentRequest;
import com.segue.backend.dto.response.ConsentResponse;
import com.segue.backend.dto.response.CustomerResponse;
import com.segue.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** F1: 고객 조회 (태블릿) + 기능명세서 5번: 고객 동의 관리 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/lookup")
    public CustomerResponse lookup(@RequestParam String phoneNumber) {
        return customerService.lookupByPhoneNumber(phoneNumber);
    }

    /** CA가 장바구니 조회 전에 고객의 데이터 이용 동의 여부를 기록한다. 반복 제출 시 마지막 의사로 덮어쓴다. */
    @PostMapping("/{customerId}/consent")
    public ConsentResponse recordConsent(@PathVariable Long customerId,
                                          @Valid @RequestBody ConsentRequest request) {
        return customerService.recordConsent(customerId, request.getAgreed());
    }

    @GetMapping("/{customerId}/consent")
    public ConsentResponse getConsent(@PathVariable Long customerId) {
        return customerService.getConsent(customerId);
    }
}
