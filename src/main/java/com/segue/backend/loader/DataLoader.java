package com.segue.backend.loader;

import com.segue.backend.domain.*;
import com.segue.backend.domain.enums.ConsentStatus;
import com.segue.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * P0 요구사항: 하드코딩된 고정 고객 2명 방식이 아니라 DB 테이블 기반으로 동작해야 하므로,
 * 애플리케이션 기동 시 이 클래스가 더미 데이터를 실제 MySQL 테이블에 INSERT 한다.
 *
 * 데모 시나리오 A·B·C·D 는 전부 "청담 본점에서 품절인 SKU S1(MCM 백팩 미디움 블랙 미디움)"을
 * 기준으로 상담을 시작하되, CA가 입력하는 고객 발화 내용에 따라 결정 엔진(F5)의 결과가
 * 갈리도록 후보 SKU(S2~S5)의 속성/재고를 설계했다. 각 시나리오가 왜 그 결과로 귀결되는지는
 * SCHEMA.md 의 "시나리오 성립 근거" 섹션에 정리되어 있다.
 *
 * 모든 inventory 행은 confirmed=true, checkedAt=기동 시각으로 시딩해 재고 신뢰도 게이트가
 * 시나리오 A~D 결과에 영향을 주지 않도록 한다 (기능명세서 6번).
 *
 * 고객 동의(기능명세서 5번)는 일부러 두 고객의 상태를 다르게 시딩한다: 김세계는 AGREE 로 미리
 * 동의되어 있어 장바구니 조회~Last Intent 플로우를 바로 데모할 수 있고, 이수현은 동의 기록이
 * 아예 없어 "동의 필요" 차단 흐름(GET /api/cart -> 403)도 그대로 데모할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final CartItemRepository cartItemRepository;
    private final ConsentRecordRepository consentRecordRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            return; // 이미 시딩된 경우 재실행하지 않음
        }

        LocalDateTime now = LocalDateTime.now();

        // ---------- 매장 ----------
        Store cheongdam = storeRepository.save(Store.builder().name("청담 본점").build());
        Store gangnam = storeRepository.save(Store.builder().name("강남 신세계점").build());

        // ---------- P1: 원제품 (청담 본점 품절, 강남점 보유) ----------
        Product p1 = productRepository.save(Product.builder()
                .name("MCM 백팩 미디움")
                .imageUrl("https://picsum.photos/seed/mcm-backpack/600/600")
                .category("백팩")
                .build());
        Sku s1 = skuRepository.save(Sku.builder()
                .product(p1).color("블랙").size("미디움")
                .material("그레인 카프스킨 가죽").weightGrams(650)
                .storageStructure("지퍼형 메인 수납 + 노트북 슬리브")
                .wearStyle("백팩(양쪽 숄더)")
                .laptopCompatible(true)
                .build());
        productAttributeRepository.save(ProductAttribute.builder()
                .sku(s1)
                .colorFamily("블랙").colorTone("쿨").material("가죽")
                .glossLevel("중간").logoVisibility("높음").logoPosition("정면중앙")
                .patternDensity("높음").silhouette("각진").structure("하드")
                .sizeGrade("미디움").strapType("패브릭+레더콤보").hardwareColor("골드")
                .usageContext("데일리").weightGrade("보통").lockType("지퍼")
                .internalStorageLevel("구획많음")
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s1).store(cheongdam)
                .currentStoreInStock(false).otherStoreInStock(true).restockPlanned(false)
                .confirmed(true).checkedAt(now)
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s1).store(gangnam)
                .currentStoreInStock(true).otherStoreInStock(true).restockPlanned(false)
                .confirmed(true).checkedAt(now)
                .build());

        // ---------- P2: 비교 체험 후보 (청담 본점 보유, S1과 소재/광택 동일) ----------
        Product p2 = productRepository.save(Product.builder()
                .name("MCM 크로스바디 백 스몰")
                .imageUrl("https://picsum.photos/seed/mcm-crossbody/600/600")
                .category("크로스바디")
                .build());
        Sku s2 = skuRepository.save(Sku.builder()
                .product(p2).color("다크브라운").size("스몰")
                .material("그레인 카프스킨 가죽").weightGrams(420)
                .storageStructure("플랩형 단일 수납")
                .wearStyle("크로스바디")
                .laptopCompatible(false)
                .build());
        productAttributeRepository.save(ProductAttribute.builder()
                .sku(s2)
                .colorFamily("브라운").colorTone("웜").material("가죽")
                .glossLevel("중간").logoVisibility("낮음").logoPosition("스트랩")
                .patternDensity("낮음").silhouette("라운드").structure("소프트")
                .sizeGrade("스몰").strapType("체인스트랩").hardwareColor("골드")
                .usageContext("이브닝").weightGrade("가벼움").lockType("플립")
                .internalStorageLevel("심플")
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s2).store(cheongdam)
                .currentStoreInStock(true).otherStoreInStock(true).restockPlanned(false)
                .confirmed(true).checkedAt(now)
                .build());

        // ---------- P3: 오늘 구매 후보 (청담 본점 보유, 노트북 수납 O) ----------
        Product p3 = productRepository.save(Product.builder()
                .name("MCM 토트백 라지")
                .imageUrl("https://picsum.photos/seed/mcm-tote/600/600")
                .category("토트백")
                .build());
        Sku s3 = skuRepository.save(Sku.builder()
                .product(p3).color("블랙").size("라지")
                .material("캔버스 + 레더 트리밍").weightGrams(780)
                .storageStructure("오픈탑 + 노트북 구획")
                .wearStyle("토트(손잡이 + 숄더스트랩)")
                .laptopCompatible(true)
                .build());
        productAttributeRepository.save(ProductAttribute.builder()
                .sku(s3)
                .colorFamily("블랙").colorTone("뉴트럴").material("캔버스")
                .glossLevel("낮음").logoVisibility("높음").logoPosition("정면하단")
                .patternDensity("높음").silhouette("사각").structure("소프트")
                .sizeGrade("라지").strapType("패브릭스트랩").hardwareColor("실버")
                .usageContext("오피스").weightGrade("무거움").lockType("마그네틱")
                .internalStorageLevel("구획많음")
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s3).store(cheongdam)
                .currentStoreInStock(true).otherStoreInStock(false).restockPlanned(false)
                .confirmed(true).checkedAt(now)
                .build());

        // ---------- P4: 필러 (청담 본점 보유, 재고 있는 제품 카드 데모용) ----------
        Product p4 = productRepository.save(Product.builder()
                .name("MCM 숄더백 미니")
                .imageUrl("https://picsum.photos/seed/mcm-shoulder/600/600")
                .category("숄더백")
                .build());
        Sku s4 = skuRepository.save(Sku.builder()
                .product(p4).color("베이지").size("미니")
                .material("자카드 패브릭").weightGrams(310)
                .storageStructure("심플 단일 수납")
                .wearStyle("숄더")
                .laptopCompatible(false)
                .build());
        productAttributeRepository.save(ProductAttribute.builder()
                .sku(s4)
                .colorFamily("베이지").colorTone("웜").material("패브릭")
                .glossLevel("낮음").logoVisibility("중간").logoPosition("스트랩")
                .patternDensity("중간").silhouette("라운드").structure("소프트")
                .sizeGrade("미니").strapType("체인스트랩").hardwareColor("골드")
                .usageContext("이브닝").weightGrade("가벼움").lockType("지퍼")
                .internalStorageLevel("심플")
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s4).store(cheongdam)
                .currentStoreInStock(true).otherStoreInStock(true).restockPlanned(false)
                .confirmed(true).checkedAt(now)
                .build());

        // ---------- P5: 두 번째 품절 제품 (F2 다중 품절 목록 데모, 입고 예정만 존재) ----------
        Product p5 = productRepository.save(Product.builder()
                .name("MCM 벨트백")
                .imageUrl("https://picsum.photos/seed/mcm-beltbag/600/600")
                .category("벨트백")
                .build());
        Sku s5 = skuRepository.save(Sku.builder()
                .product(p5).color("블랙").size("스몰")
                .material("사피아노 가죽").weightGrams(280)
                .storageStructure("지퍼형 단일 수납")
                .wearStyle("벨트 / 크로스바디 겸용")
                .laptopCompatible(false)
                .build());
        productAttributeRepository.save(ProductAttribute.builder()
                .sku(s5)
                .colorFamily("블랙").colorTone("쿨").material("가죽")
                .glossLevel("높음").logoVisibility("중간").logoPosition("스트랩")
                .patternDensity("낮음").silhouette("라운드").structure("하드")
                .sizeGrade("미니").strapType("벨트스트랩").hardwareColor("건메탈")
                .usageContext("데일리").weightGrade("가벼움").lockType("지퍼")
                .internalStorageLevel("심플")
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(s5).store(cheongdam)
                .currentStoreInStock(false).otherStoreInStock(false).restockPlanned(true)
                .confirmed(true).checkedAt(now)
                .build());

        // ---------- 고객 ----------
        Customer kim = customerRepository.save(Customer.builder()
                .name("김세계").phoneNumber("010-1234-5678").build());
        Customer lee = customerRepository.save(Customer.builder()
                .name("이수현").phoneNumber("010-9876-5432").build());

        // ---------- 고객 동의 (기능명세서 5번) ----------
        consentRecordRepository.save(ConsentRecord.builder()
                .customer(kim).status(ConsentStatus.AGREE)
                .scope("장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인")
                .consentedAt(now)
                .build());
        // 이수현은 의도적으로 동의 기록을 남기지 않는다 -> "동의 필요" 차단 흐름 데모용.

        // ---------- 장바구니 ----------
        // 김세계 장바구니: 품절 2건(S1, S5) + 보유 1건(S4) -> 태블릿 F2 화면에서
        // "Last Intent 시작" 버튼 2개 + "제품 확인하기" 버튼 1개가 동시에 보이는 것을 데모.
        cartItemRepository.save(CartItem.builder()
                .customer(kim).sku(s1).color(s1.getColor()).size(s1.getSize())
                .savedAt(now.minusMinutes(5)).build());
        cartItemRepository.save(CartItem.builder()
                .customer(kim).sku(s5).color(s5.getColor()).size(s5.getSize())
                .savedAt(now.minusMinutes(20)).build());
        cartItemRepository.save(CartItem.builder()
                .customer(kim).sku(s4).color(s4.getColor()).size(s4.getSize())
                .savedAt(now.minusMinutes(40)).build());

        // 이수현 장바구니: 보유 재고 1건 (단, 동의 전이므로 GET /api/cart 는 403)
        cartItemRepository.save(CartItem.builder()
                .customer(lee).sku(s2).color(s2.getColor()).size(s2.getSize())
                .savedAt(now.minusHours(1)).build());
    }
}
