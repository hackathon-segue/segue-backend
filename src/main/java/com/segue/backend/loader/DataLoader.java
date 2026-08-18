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
 * MCM 실제 판매 제품 12개 기준 데모 데이터셋 ("최종 선정 12개 가방" 기획안 그대로 반영).
 * 전부 SKU 1(M Diamond 비세토스 레더 믹스 · 꼬냑)에서 출발한다 — 청담 본점 품절 공통 기준 제품.
 *
 * 구성: 원제품 1개 + 페르소나별 정답 3개 + 혼동 후보(근접 오답) 6개 + 명확한 비적합 후보 2개.
 * 각 후보가 왜 정답/오답인지는 SCHEMA.md 의 "MCM 12개 제품 데모 시나리오" 섹션에 정리되어 있다.
 *
 * 원제품(SKU 1)의 타 매장(강남 신세계점) 재고는 항상 "있음"으로 고정 시딩한다. DecisionEngine 은
 * essential 조건을 만족하는 매장 내 대안이 있으면 그걸 먼저 제시하므로(③-보조 폴백), 페르소나 1·2·3·5는
 * 이 값과 무관하게 원래 의도대로 동작하고, essential 이 원제품에만 유일하게 성립하는 페르소나 4만
 * 자연스럽게 EXACT_PRODUCT(정확한 제품 확인)로 빠진다 — 수동 DB 토글이 필요 없다.
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
            refreshInventoryCheckedAt();
            return; // 이미 시딩된 경우 재실행하지 않음
        }

        LocalDateTime now = LocalDateTime.now();

        // ---------- 매장 ----------
        Store cheongdam = storeRepository.save(Store.builder().name("청담 본점").build());
        Store gangnam = storeRepository.save(Store.builder().name("강남 신세계점").build());

        // ---------- 1. 원제품 (공통 미보유 기준 제품) ----------
        Sku s1 = createSku(cheongdam, gangnam,
                "M Diamond 비세토스 레더 믹스", "핸드백",
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 나파 송아지 가죽 트림", 480,
                "지퍼 클로저 + 내부 포켓", "핸드백/크로스바디 겸용", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "미디움", "벨트스트랩", "골드", "데일리", "가벼움", "마그네틱", "심플", "다이아몬드컷아웃"),
                false, true, true); // 청담 품절, 강남 재고 있음 (페르소나 4 "정확한 제품 확인" 경로)

        // ---------- 2. 디자인형 정답: M Diamond 엠보스드 레더 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "M Diamond 엠보스드 레더", "핸드백",
                "블랙", "M", "엠보스드 레더", 470,
                "지퍼 클로저 + 내부 포켓", "핸드백/크로스바디 겸용", false, null,
                Attr.of("블랙", "쿨", "가죽", "중간", "낮음", "정면하단", "낮음", "사각", "하드",
                        "미디움", "벨트스트랩", "골드", "데일리", "가벼움", "마그네틱", "심플", "다이아몬드컷아웃"),
                true, true, false);

        // ---------- 3. 시그니처·소재형 정답: M New Liz 비세토스 쇼퍼 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "M New Liz 비세토스 쇼퍼", "쇼퍼백",
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 천연 가죽 트림", 520,
                "탈착형 지퍼 파우치 포함, 오픈탑", "숄더", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "골드", "데일리", "보통", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 4. 기능형 정답: L Aren 비세토스 N/S 토트 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "L Aren 비세토스 N/S 토트", "토트백",
                "블랙", "L", "비세토스 모노그램 캔버스 + 가죽 트림", 780,
                "16인치 노트북·태블릿 포켓 + 다수의 내부 포켓", "토트(손잡이 + 숄더스트랩)", true, 16,
                Attr.of("블랙", "쿨", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "라지", "패브릭스트랩", "건메탈", "오피스", "무거움", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 5. 디자인 혼동 후보 A: S 뮌헨 비세토스 토트 · 꼬냑 (실루엣만 비슷, 핸들 다름) ----------
        createSku(cheongdam, gangnam,
                "S 뮌헨 비세토스 토트", "토트백",
                "꼬냑", "S", "비세토스 모노그램 캔버스 + 가죽 핸들", 430,
                "지퍼 클로저 + 심플 수납", "토트/크로스바디 겸용", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "스몰", "벨트스트랩", "골드", "데일리", "가벼움", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 6. 디자인 혼동 후보 B: 미니 Diamond 카프 레더 숄더백 · 블랙 (핸들 일부만 유사) ----------
        createSku(cheongdam, gangnam,
                "미니 Diamond 카프 레더 숄더백", "숄더백",
                "블랙", "미니", "카프 레더", 290,
                "플랩형 단일 수납", "숄더", false, null,
                Attr.of("블랙", "쿨", "가죽", "중간", "낮음", "정면하단", "낮음", "라운드", "소프트",
                        "미니", "체인스트랩", "골드", "이브닝", "가벼움", "플립", "심플", "일반"),
                true, true, false);

        // ---------- 7. 소재 혼동 후보 A: S Milla 그레인 가죽 토트 · 오렌지에이드 ----------
        createSku(cheongdam, gangnam,
                "S Milla 그레인 가죽 토트", "토트백",
                "오렌지에이드", "S", "그레인 가죽", 460,
                "지퍼 클로저 + 심플 수납", "토트/크로스바디 겸용", false, null,
                Attr.of("오렌지", "웜", "가죽", "높음", "낮음", "정면하단", "낮음", "각진", "하드",
                        "스몰", "벨트스트랩", "골드", "데일리", "보통", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 8. 시그니처 혼동 후보 B: S Aren 비세토스 듀오 호보 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "S Aren 비세토스 듀오 호보", "크로스바디",
                "블랙", "S", "비세토스 모노그램 캔버스 + 나파 가죽", 350,
                "지퍼형 심플 수납", "크로스바디/숄더", false, null,
                Attr.of("블랙", "쿨", "캔버스", "낮음", "중간", "정면중앙", "중간", "라운드", "소프트",
                        "스몰", "체인스트랩", "실버", "이브닝", "가벼움", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 9. 기능 혼동 후보 A: M Stark 사이드 스터드 비세토스 백팩 · 꼬냑 (13인치까지만) ----------
        createSku(cheongdam, gangnam,
                "M Stark 사이드 스터드 비세토스 백팩", "백팩",
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 가죽 트림", 650,
                "지퍼형 메인 수납 + 13인치 노트북 슬리브", "백팩(양쪽 숄더)", true, 13,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "골드", "오피스", "보통", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 10. 기능 혼동 후보 B: M Aren ECONYL 가죽 백팩 · 그린 (스펙은 맞지만 오늘 재고 없음) ----------
        createSku(cheongdam, gangnam,
                "M Aren ECONYL 가죽 백팩", "백팩",
                "그린", "M", "ECONYL 재생나일론 + 가죽 트림", 600,
                "16인치 노트북 슬리브 + 다수 포켓", "백팩(양쪽 숄더)", true, 16,
                Attr.of("그린", "쿨", "패브릭", "중간", "낮음", "정면하단", "낮음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "건메탈", "오피스", "보통", "지퍼", "구획많음", "일반"),
                false, true, true); // 청담 없음, 강남 있음(타 매장 재고 있음)

        // ---------- 11. 명확한 비적합 후보 A: 미니 Tracy 비세토스 레더 믹스 크로스바디 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "미니 Tracy 비세토스 레더 믹스 크로스바디", "크로스바디",
                "꼬냑", "미니", "비세토스 모노그램 캔버스 + 가죽 트림", 220,
                "플립형 심플 수납", "크로스바디", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "각진", "소프트",
                        "미니", "체인스트랩", "골드", "이브닝", "가벼움", "플립", "심플", "일반"),
                true, true, false);

        // ---------- 12. 명확한 비적합 후보 B: S Pina 비세토스 탬버린 백 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "S Pina 비세토스 탬버린 백", "크로스바디",
                "꼬냑", "S", "비세토스 모노그램 캔버스", 280,
                "지퍼형 단일 수납", "크로스바디", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "라운드", "하드",
                        "스몰", "체인스트랩", "골드", "이브닝", "가벼움", "지퍼", "심플", "일반"),
                false, true, false); // 재고 없음 (명확한 비적합 후보)

        // ---------- 고객 ----------
        Customer kim = customerRepository.save(Customer.builder()
                .name("김세계").phoneNumber("010-1234-5678").build());
        Customer lee = customerRepository.save(Customer.builder()
                .name("이수현").phoneNumber("010-9876-5432").build());

        // ---------- 고객 동의 ----------
        consentRecordRepository.save(ConsentRecord.builder()
                .customer(kim).status(ConsentStatus.AGREE)
                .scope("장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인")
                .consentedAt(now)
                .build());
        // 이수현은 의도적으로 동의 기록을 남기지 않는다 -> "동의 필요" 차단 흐름 데모용.

        // ---------- 장바구니: 세 페르소나가 공통으로 담는 원제품(SKU 1) ----------
        cartItemRepository.save(CartItem.builder()
                .customer(kim).sku(s1).color(s1.getColor()).size(s1.getSize())
                .savedAt(now.minusMinutes(5)).build());

        // 이수현 장바구니: 시그니처형 정답 SKU 로 보유 재고 케이스 데모 (동의 전이므로 GET /api/cart 는 403)
        cartItemRepository.save(CartItem.builder()
                .customer(lee).sku(s1).color(s1.getColor()).size(s1.getSize())
                .savedAt(now.minusHours(1)).build());
    }

    /**
     * 이미 시딩된 DB 로 재기동할 때 재고 확인 시각을 현재로 동기화한다.
     *
     * checked_at 은 최초 시딩 때 한 번만 찍히는데 MySQL 은 영속이므로, 갱신하지 않으면 시딩 시점이
     * 그대로 고정된다. inventory.freshness-hours(기본 12시간)를 넘긴 순간부터 DecisionEngine 의
     * isReliable() 이 모든 행에 대해 false 를 반환해 4가지 결과가 전부 추가 상담으로 수렴하며,
     * 재시작으로는 복구되지 않는다.
     *
     * 실제 매장에서도 시스템 기동 시 POS 재고를 한 번 동기화하므로 의미상으로도 동일하다.
     * confirmed(확인 여부) 는 데이터가 가진 원래 값을 그대로 두고 시각만 갱신한다.
     */
    private void refreshInventoryCheckedAt() {
        LocalDateTime now = LocalDateTime.now();
        inventoryRepository.findAll().forEach(inventory -> inventory.setCheckedAt(now));
    }

    private Sku createSku(Store cheongdam, Store gangnam,
                           String productName, String category,
                           String color, String size, String materialText, Integer weightGrams,
                           String storageStructure, String wearStyle,
                           boolean laptopCompatible, Integer laptopMaxInch,
                           Attr attr,
                           boolean inStockAtCheongdam, boolean confirmed, boolean inStockAtGangnam) {
        Product product = productRepository.save(Product.builder()
                .name(productName)
                .imageUrl("https://picsum.photos/seed/" + slug(productName) + "/600/600")
                .category(category)
                .build());

        Sku sku = skuRepository.save(Sku.builder()
                .product(product).color(color).size(size)
                .material(materialText).weightGrams(weightGrams)
                .storageStructure(storageStructure).wearStyle(wearStyle)
                .laptopCompatible(laptopCompatible).laptopMaxInch(laptopMaxInch)
                .build());

        productAttributeRepository.save(ProductAttribute.builder()
                .sku(sku)
                .colorFamily(attr.colorFamily).colorTone(attr.colorTone).material(attr.material)
                .glossLevel(attr.glossLevel).logoVisibility(attr.logoVisibility).logoPosition(attr.logoPosition)
                .patternDensity(attr.patternDensity).silhouette(attr.silhouette).structure(attr.structure)
                .sizeGrade(attr.sizeGrade).strapType(attr.strapType).hardwareColor(attr.hardwareColor)
                .usageContext(attr.usageContext).weightGrade(attr.weightGrade).lockType(attr.lockType)
                .internalStorageLevel(attr.internalStorageLevel).handleType(attr.handleType)
                .build());

        LocalDateTime now = LocalDateTime.now();
        inventoryRepository.save(Inventory.builder()
                .sku(sku).store(cheongdam)
                .currentStoreInStock(inStockAtCheongdam).otherStoreInStock(inStockAtGangnam)
                .restockPlanned(false).confirmed(confirmed).checkedAt(now)
                .build());
        inventoryRepository.save(Inventory.builder()
                .sku(sku).store(gangnam)
                .currentStoreInStock(inStockAtGangnam).otherStoreInStock(inStockAtCheongdam)
                .restockPlanned(false).confirmed(confirmed).checkedAt(now)
                .build());

        return sku;
    }

    private String slug(String productName) {
        return productName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /** ProductAttribute 16(+1) 개 필드를 인자 순서로 한 번에 받는 값 객체 (DataLoader 내부 전용). */
    private record Attr(String colorFamily, String colorTone, String material, String glossLevel,
                         String logoVisibility, String logoPosition, String patternDensity, String silhouette,
                         String structure, String sizeGrade, String strapType, String hardwareColor,
                         String usageContext, String weightGrade, String lockType, String internalStorageLevel,
                         String handleType) {
        static Attr of(String colorFamily, String colorTone, String material, String glossLevel,
                       String logoVisibility, String logoPosition, String patternDensity, String silhouette,
                       String structure, String sizeGrade, String strapType, String hardwareColor,
                       String usageContext, String weightGrade, String lockType, String internalStorageLevel,
                       String handleType) {
            return new Attr(colorFamily, colorTone, material, glossLevel, logoVisibility, logoPosition,
                    patternDensity, silhouette, structure, sizeGrade, strapType, hardwareColor, usageContext,
                    weightGrade, lockType, internalStorageLevel, handleType);
        }
    }
}
