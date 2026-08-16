package com.segue.backend.dto.response;

import com.segue.backend.domain.ConsentRecord;
import com.segue.backend.domain.enums.ConsentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentResponse {
    private Long customerId;
    private ConsentStatus status;
    private String scope;
    private LocalDateTime consentedAt;

    public static ConsentResponse from(ConsentRecord record) {
        return ConsentResponse.builder()
                .customerId(record.getCustomer().getId())
                .status(record.getStatus())
                .scope(record.getScope())
                .consentedAt(record.getConsentedAt())
                .build();
    }
}
