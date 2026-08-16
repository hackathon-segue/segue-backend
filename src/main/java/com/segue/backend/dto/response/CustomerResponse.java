package com.segue.backend.dto.response;

import com.segue.backend.domain.Customer;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private Long id;
    private String name;
    private String phoneNumber;
    /** 기능명세서 5번: 최신 확정 동의 상태가 AGREE 인지 여부. false 면 장바구니 조회 전에 동의부터 받아야 한다. */
    private boolean hasConsented;

    public static CustomerResponse from(Customer customer, boolean hasConsented) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phoneNumber(customer.getPhoneNumber())
                .hasConsented(hasConsented)
                .build();
    }
}
