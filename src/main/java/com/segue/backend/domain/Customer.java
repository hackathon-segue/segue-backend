package com.segue.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 30, unique = true)
    private String phoneNumber;

    /**
     * 로그인 아이디. 항상 소문자로 정규화해 저장한다.
     *
     * ddl-auto=update 로 기존 행이 있는 테이블에 컬럼을 추가하므로 NOT NULL 을 걸지 않는다.
     * (컬럼 추가 시점에는 값이 없고, DataLoader 가 기동하면서 시드 계정에 채워 넣는다)
     * 값 존재 여부는 요청 DTO 검증과 서비스 계층에서 보장한다.
     */
    @Column(name = "email", length = 255, unique = true)
    private String email;

    /** BCrypt 해시. 평문은 어디에도 저장하지 않으며 응답 DTO 에도 절대 포함하지 않는다. */
    @Column(name = "password", length = 100)
    private String password;
}
