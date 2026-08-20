package com.segue.backend.controller;

import com.segue.backend.dto.request.ConsentRequest;
import com.segue.backend.dto.request.LoginRequest;
import com.segue.backend.dto.request.PasswordChangeRequest;
import com.segue.backend.dto.request.ProfileUpdateRequest;
import com.segue.backend.dto.request.SignupRequest;
import com.segue.backend.dto.response.ConsentResponse;
import com.segue.backend.dto.response.CustomerResponse;
import com.segue.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** F1: 고객 조회 (태블릿) + 기능명세서 5번: 고객 동의 관리 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** 고객 모바일 회원가입. 전화번호는 CA 태블릿 조회 키라 필수다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse signup(@Valid @RequestBody SignupRequest request) {
        return customerService.signup(request);
    }

    /** 고객 모바일 로그인. 세션/토큰 없이 고객 정보만 반환하므로 프론트가 customerId 를 보관한다. */
    @PostMapping("/login")
    public CustomerResponse login(@Valid @RequestBody LoginRequest request) {
        return customerService.login(request);
    }

    /** 프로필 편집 (성명·이메일·전화번호) */
    @PatchMapping("/{customerId}")
    public CustomerResponse updateProfile(@PathVariable Long customerId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return customerService.updateProfile(customerId, request);
    }

    /** 비밀번호 변경 (현재 비밀번호 확인 필요) */
    @PatchMapping("/{customerId}/password")
    public CustomerResponse changePassword(@PathVariable Long customerId,
                                             @Valid @RequestBody PasswordChangeRequest request) {
        return customerService.changePassword(customerId, request);
    }

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
