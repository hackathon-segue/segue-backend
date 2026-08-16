package com.segue.backend.dto.response;

import com.segue.backend.domain.ConsultationResult;
import com.segue.backend.domain.enums.ExecutionStatus;
import com.segue.backend.domain.enums.ResultType;
import lombok.*;

import java.time.LocalDateTime;

/** F8: 고객 모바일에서 조회하는 상담 결과. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationResultResponse {
    private Long id;
    private Long skuId;
    private String productName;
    private String imageUrl;
    private ResultType resultType;
    private String recommendedPath;
    private String coreConditions;
    private LocalDateTime consultedAt;

    /** 기능명세서 7번: 실행 버튼 이후 후속 처리 상태 */
    private ExecutionStatus executionStatus;
    private String executionNote;
    private LocalDateTime executionUpdatedAt;

    public static ConsultationResultResponse from(ConsultationResult r) {
        return ConsultationResultResponse.builder()
                .id(r.getId())
                .skuId(r.getSku().getId())
                .productName(r.getSku().getProduct().getName())
                .imageUrl(r.getSku().getProduct().getImageUrl())
                .resultType(r.getResultType())
                .recommendedPath(r.getRecommendedPath())
                .coreConditions(r.getCoreConditions())
                .consultedAt(r.getConsultedAt())
                .executionStatus(r.getExecutionStatus())
                .executionNote(r.getExecutionNote())
                .executionUpdatedAt(r.getExecutionUpdatedAt())
                .build();
    }
}
