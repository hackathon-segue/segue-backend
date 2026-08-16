package com.segue.backend.domain;

import com.segue.backend.domain.enums.ConsentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 기능명세서 5. 고객 동의 및 상담 데이터 관리.
 * 고객 1명당 최신 동의 의사 1건만 유지한다 (반복 제출 시 마지막 의사로 덮어씀).
 */
@Entity
@Table(name = "customer_consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConsentStatus status;

    /** 동의 범위: 장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인 */
    @Column(name = "scope", nullable = false, length = 200)
    private String scope;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;
}
