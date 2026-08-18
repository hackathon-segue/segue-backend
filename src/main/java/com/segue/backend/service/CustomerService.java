package com.segue.backend.service;

import com.segue.backend.domain.ConsentRecord;
import com.segue.backend.domain.Customer;
import com.segue.backend.domain.enums.ConsentStatus;
import com.segue.backend.dto.response.ConsentResponse;
import com.segue.backend.dto.response.CustomerResponse;
import com.segue.backend.exception.ConsentRequiredException;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.repository.ConsentRecordRepository;
import com.segue.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** F1: 고객 조회 + 기능명세서 5번: 고객 동의 관리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private static final String CONSENT_SCOPE = "장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인";

    private final CustomerRepository customerRepository;
    private final ConsentRecordRepository consentRecordRepository;

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
