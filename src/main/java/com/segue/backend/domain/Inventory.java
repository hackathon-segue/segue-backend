package com.segue.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "current_store_in_stock", nullable = false)
    private Boolean currentStoreInStock;

    @Column(name = "other_store_in_stock", nullable = false)
    private Boolean otherStoreInStock;

    @Column(name = "restock_planned", nullable = false)
    private Boolean restockPlanned;

    /**
     * 기능명세서 6번: 재고·입고 가능성 검증. 이 행의 보유 여부 값이 실제로 확인된 값인지(예: CA/POS 확인)
     * 를 나타낸다. false 면 규칙 엔진은 이 값을 확정 구매 경로로 사용하지 않고 추가 확인으로 돌린다.
     */
    @Column(name = "confirmed", nullable = false)
    private Boolean confirmed;

    /** 이 재고 상태의 기준 시점. 지나치게 오래되면(신선도 기준 초과) 미확인과 동일하게 취급한다. */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
