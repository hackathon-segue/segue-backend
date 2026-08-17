package com.segue.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sku")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "color", nullable = false, length = 50)
    private String color;

    @Column(name = "size", nullable = false, length = 50)
    private String size;

    @Column(name = "material", length = 200)
    private String material;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "storage_structure", length = 200)
    private String storageStructure;

    @Column(name = "wear_style", length = 100)
    private String wearStyle;

    @Column(name = "laptop_compatible", nullable = false)
    private Boolean laptopCompatible;

    /** 노트북 수납 최대 인치 (13 | 16). laptopCompatible=false 면 null. */
    @Column(name = "laptop_max_inch")
    private Integer laptopMaxInch;
}
