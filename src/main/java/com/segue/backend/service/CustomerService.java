package com.segue.backend.service;

import com.segue.backend.domain.ConsentRecord;
import com.segue.backend.domain.Customer;
import com.segue.backend.domain.enums.ConsentStatus;
import com.segue.backend.dto.request.LoginRequest;
import com.segue.backend.dto.request.PasswordChangeRequest;
import com.segue.backend.dto.request.ProfileUpdateRequest;
import com.segue.backend.dto.request.SignupRequest;
import com.segue.backend.dto.response.ConsentResponse;
import com.segue.backend.dto.response.CustomerResponse;
import com.segue.backend.exception.ConflictException;
import com.segue.backend.exception.ConsentRequiredException;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.exception.UnauthorizedException;
import com.segue.backend.repository.ConsentRecordRepository;
import com.segue.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** F1: 고객 조회 + 기능명세서 5번: 고객 동의 관리 + 고객 모바일 회원가입/로그인/프로필 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private static final String CONSENT_SCOPE = "장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인";

    /**
     * 로그인 실패 사유를 구분해서 알려주지 않는다. "없는 이메일"과 "틀린 비밀번호"를 나누면
     * 특정 이메일의 가입 여부를 확인할 수 있게 되므로 동일한 문구를 반환한다.
     */
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 일치하지 않습니다.";

    private final CustomerRepository customerRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final PasswordEncoder passwordEncoder;

    /** 고객 모바일 회원가입. 이메일/전화번호 중복은 409. */
    @Transactional
    public CustomerResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());
        requireEmailAvailable(email, null);
        requirePhoneNumberAvailable(request.getPhoneNumber(), null);

        Customer saved = customerRepository.save(Customer.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber().trim())
                .build());
        // 가입 직후에는 데이터 이용 동의 기록이 없다. 동의는 매장에서 CA 가 받는다 (기능명세서 5번).
        return CustomerResponse.from(saved, false);
    }

    /** 고객 모바일 로그인. 세션/토큰을 만들지 않고 고객 정보만 반환한다. */
    public CustomerResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED));
        if (customer.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED);
        }
        return CustomerResponse.from(customer, hasConsented(customer.getId()));
    }

    /** 프로필 편집(성명·이메일·전화번호). 비밀번호는 changePassword 에서만 바꾼다. */
    @Transactional
    public CustomerResponse updateProfile(Long customerId, ProfileUpdateRequest request) {
        Customer customer = getById(customerId);
        String email = normalizeEmail(request.getEmail());
        requireEmailAvailable(email, customerId);
        requirePhoneNumberAvailable(request.getPhoneNumber(), customerId);

        customer.setName(request.getName().trim());
        customer.setEmail(email);
        customer.setPhoneNumber(request.getPhoneNumber().trim());
        return CustomerResponse.from(customer, hasConsented(customerId));
    }

    /** 비밀번호 변경. 현재 비밀번호가 일치해야 한다. */
    @Transactional
    public CustomerResponse changePassword(Long customerId, PasswordChangeRequest request) {
        Customer customer = getById(customerId);
        if (customer.getPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), customer.getPassword())) {
            throw new UnauthorizedException("현재 비밀번호가 일치하지 않습니다.");
        }
        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        return CustomerResponse.from(customer, hasConsented(customerId));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** excludeCustomerId 는 프로필 편집 시 자기 자신을 중복으로 보지 않기 위한 예외 대상이다. */
    private void requireEmailAvailable(String email, Long excludeCustomerId) {
        customerRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(excludeCustomerId)) {
                throw new ConflictException("이미 사용 중인 이메일 주소입니다.");
            }
        });
    }

    private void requirePhoneNumberAvailable(String phoneNumber, Long excludeCustomerId) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
        customerRepository.findByPhoneNumberDigits(digits).ifPresent(existing -> {
            if (!existing.getId().equals(excludeCustomerId)) {
                throw new ConflictException("이미 사용 중인 전화번호입니다.");
            }
        });
    }

    public CustomerResponse lookupByPhoneNumber(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
        Customer customer = customerRepository.findByPhoneNumberDigits(digits)
                .orElseThrow(() -> new NotFoundException("일치하는 고객 정보를 찾을 수 없습니다. 회원 정보를 다시 확인해 주세요."));
        boolean hasConsented = hasConsented(customer.getId());
        return CustomerResponse.from(customer, hasConsented);
    }

    public Customer getById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("고객을 찾을 수 없습니다. id=" + customerId));
    }

    /** 동의 상태가 없거나(=한 번도 응답하지 않음) 마지막 의사가 DISAGREE 이면 false. */
    public boolean hasConsented(Long customerId) {
        return consentRecordRepository.findByCustomerId(customerId)
                .map(r -> r.getStatus() == ConsentStatus.AGREE)
                .orElse(false);
    }

    /** 동의가 없으면 데이터 조회/저장을 차단한다. CartService/ConsultationService 에서 호출. */
    public void requireConsent(Long customerId) {
        if (!hasConsented(customerId)) {
            throw new ConsentRequiredException(
                    "고객 동의가 필요합니다. 장바구니 조회·상담 결과 저장 전에 데이터 이용 동의를 먼저 받아 주세요.");
        }
    }

    @Transactional
    public ConsentResponse recordConsent(Long customerId, boolean agreed) {
        Customer customer = getById(customerId);
        ConsentRecord record = consentRecordRepository.findByCustomerId(customerId)
                .orElseGet(() -> ConsentRecord.builder().customer(customer).build());
        // 동일 고객이 반복 제출하면 마지막으로 확정된 의사만 적용한다 (upsert).
        record.setStatus(agreed ? ConsentStatus.AGREE : ConsentStatus.DISAGREE);
        record.setScope(CONSENT_SCOPE);
        record.setConsentedAt(LocalDateTime.now());
        return ConsentResponse.from(consentRecordRepository.save(record));
    }

    public ConsentResponse getConsent(Long customerId) {
        getById(customerId);
        ConsentRecord record = consentRecordRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("아직 기록된 동의 정보가 없습니다."));
        return ConsentResponse.from(record);
    }
}
