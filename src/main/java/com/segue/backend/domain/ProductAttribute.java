package com.segue.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 매칭용 사전 입력 속성. DecisionEngine이 고객 필수/선호 조건과 문자열로 비교하는 기준값이므로
 * 값의 어휘(vocabulary)는 prompts/intent.txt 에 AI 프롬프트로 전달되는 목록과 반드시 일치해야 한다.
 */
@Entity
@Table(name = "product_attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false, unique = true)
    private Sku sku;

    @Column(name = "color_family", length = 50)
    private String colorFamily;

    @Column(name = "color_tone", length = 50)
    private String colorTone;

    @Column(name = "material", length = 100)
    private String material;

    @Column(name = "gloss_level", length = 50)
    private String glossLevel;

    @Column(name = "logo_visibility", length = 50)
    private String logoVisibility;

    @Column(name = "logo_position", length = 100)
    private String logoPosition;

    @Column(name = "pattern_density", length = 50)
    private String patternDensity;

    @Column(name = "silhouette", length = 100)
    private String silhouette;

    @Column(name = "structure", length = 100)
    private String structure;

    @Column(name = "size_grade", length = 50)
    private String sizeGrade;

    @Column(name = "strap_type", length = 100)
    private String strapType;

    @Column(name = "hardware_color", length = 50)
    private String hardwareColor;

    @Column(name = "usage_context", length = 100)
    private String usageContext;

    @Column(name = "weight_grade", length = 50)
    private String weightGrade;

    @Column(name = "lock_type", length = 100)
    private String lockType;

    @Column(name = "internal_storage_level", length = 50)
    private String internalStorageLevel;

    /** 핸들 디자인 (예: 다이아몬드컷아웃 | 일반). 실루엣/구조만으로 구분 안 되는 핸들 디테일용 */
    @Column(name = "handle_type", length = 50)
    private String handleType;
}
