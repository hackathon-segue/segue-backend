package com.segue.backend.domain;

import com.segue.backend.domain.enums.ExecutionStatus;
import com.segue.backend.domain.enums.ResultType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "consultation_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 상담이 시작된 원제품 SKU (품절되었던 SKU) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 40)
    private ResultType resultType;

    /** 추천 제품 또는 확보 경로에 대한 설명 (예: 제품명, "강남점 재고 확인" 등) */
    @Column(name = "recommended_path", nullable = false, length = 500)
    private String recommendedPath;

    /** 고객이 중요하게 여긴 핵심 조건 요약 */
    @Column(name = "core_conditions", nullable = false, length = 1000)
    private String coreConditions;

    @Column(name = "consulted_at", nullable = false)
    private LocalDateTime consultedAt;

    /** 기능명세서 7번: 실행 버튼 이후 후속 처리 상태 (요청접수/실행불가/후속확인필요) */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private ExecutionStatus executionStatus;

    /** 실행 불가·후속확인필요 상태일 때의 사유/안내. REQUESTED 상태에서는 비워둘 수 있다. */
    @Column(name = "execution_note", length = 500)
    private String executionNote;

    @Column(name = "execution_updated_at", nullable = false)
    private LocalDateTime executionUpdatedAt;
}
