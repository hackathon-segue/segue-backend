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
 * 애플리케이션 기동 시 이 클래스가 더미 데이터를 실제 MySQL 테이블에 반영한다.
 *
 * <b>이 시딩은 멱등(idempotent)하다.</b> 예전에는 "고객이 한 명이라도 있으면 아무것도 하지 않고 반환"
 * 하는 가드가 있었는데, MySQL 은 영속이므로 코드에서 시드 데이터를 고쳐도 기존 DB 에는 영원히
 * 반영되지 않는 문제가 있었다. 실제로 두 번 발생했다.
 * <ul>
 *   <li>checked_at 이 최초 시딩 시각에 고정되어 12시간 후 모든 재고가 미확인 처리됨</li>
 *   <li>제품 이미지 경로를 picsum 에서 /images/products/*.png 로 바꿨는데 DB 는 picsum 유지</li>
 * </ul>
 * 그래서 매 기동마다 자연 키(매장명, 제품명, 제품+컬러+사이즈, SKU+매장, 전화번호)로 찾아
 * 있으면 갱신하고 없으면 생성한다. ID 는 유지되고, 고객이 만든 데이터(장바구니 추가분,
 * 상담 결과, 동의 기록)는 건드리지 않는다.
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
        LocalDateTime now = LocalDateTime.now();

        // ---------- 매장 ----------
        Store cheongdam = upsertStore("청담 본점");
        Store gangnam = upsertStore("강남 신세계점");

        // ---------- 1. 원제품 (공통 미보유 기준 제품) ----------
        Sku s1 = createSku(cheongdam, gangnam,
                "M Diamond 비세토스 레더 믹스", "핸드백",
                "/images/products/bag1.png",
                1590000,
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 나파 송아지 가죽 트림", 480,
                "지퍼 클로저 + 내부 포켓", "핸드백/크로스바디 겸용", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "미디움", "벨트스트랩", "골드", "데일리", "가벼움", "마그네틱", "심플", "다이아몬드컷아웃"),
                false, true, true); // 청담 품절, 강남 재고 있음 (페르소나 4 "정확한 제품 확인" 경로)

        // ---------- 2. 디자인형 정답: M Diamond 엠보스드 레더 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "M Diamond 엠보스드 레더", "핸드백",
                "/images/products/bag2.png",
                1850000,
                "블랙", "M", "엠보스드 레더", 470,
                "지퍼 클로저 + 내부 포켓", "핸드백/크로스바디 겸용", false, null,
                Attr.of("블랙", "쿨", "가죽", "중간", "낮음", "정면하단", "낮음", "사각", "하드",
                        "미디움", "벨트스트랩", "골드", "데일리", "가벼움", "마그네틱", "심플", "다이아몬드컷아웃"),
                true, true, false);

        // ---------- 3. 시그니처·소재형 정답: M New Liz 비세토스 쇼퍼 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "M New Liz 비세토스 쇼퍼", "쇼퍼백",
                "/images/products/bag3.png",
                1090000,
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 천연 가죽 트림", 520,
                "탈착형 지퍼 파우치 포함, 오픈탑", "숄더", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "골드", "데일리", "보통", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 4. 기능형 정답: L Aren 비세토스 N/S 토트 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "L Aren 비세토스 N/S 토트", "토트백",
                "/images/products/bag4.png",
                1390000,
                "블랙", "L", "비세토스 모노그램 캔버스 + 가죽 트림", 780,
                "16인치 노트북·태블릿 포켓 + 다수의 내부 포켓", "토트(손잡이 + 숄더스트랩)", true, 16,
                Attr.of("블랙", "쿨", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "라지", "패브릭스트랩", "건메탈", "오피스", "무거움", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 5. 디자인 혼동 후보 A: S 뮌헨 비세토스 토트 · 꼬냑 (실루엣만 비슷, 핸들 다름) ----------
        createSku(cheongdam, gangnam,
                "S 뮌헨 비세토스 토트", "토트백",
                "/images/products/bag5.png",
                1290000,
                "꼬냑", "S", "비세토스 모노그램 캔버스 + 가죽 핸들", 430,
                "지퍼 클로저 + 심플 수납", "토트/크로스바디 겸용", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "하드",
                        "스몰", "벨트스트랩", "골드", "데일리", "가벼움", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 6. 디자인 혼동 후보 B: 미니 Diamond 카프 레더 숄더백 · 블랙 (핸들 일부만 유사) ----------
        createSku(cheongdam, gangnam,
                "미니 Diamond 카프 레더 숄더백", "숄더백",
                "/images/products/bag6.png",
                1050000,
                "블랙", "미니", "카프 레더", 290,
                "플랩형 단일 수납", "숄더", false, null,
                Attr.of("블랙", "쿨", "가죽", "중간", "낮음", "정면하단", "낮음", "라운드", "소프트",
                        "미니", "체인스트랩", "골드", "이브닝", "가벼움", "플립", "심플", "일반"),
                true, true, false);

        // ---------- 7. 소재 혼동 후보 A: S Milla 그레인 가죽 토트 · 오렌지에이드 ----------
        createSku(cheongdam, gangnam,
                "S Milla 그레인 가죽 토트", "토트백",
                "/images/products/bag7.png",
                1690000,
                "오렌지에이드", "S", "그레인 가죽", 460,
                "지퍼 클로저 + 심플 수납", "토트/크로스바디 겸용", false, null,
                Attr.of("오렌지", "웜", "가죽", "높음", "낮음", "정면하단", "낮음", "사각", "하드",
                        "스몰", "벨트스트랩", "골드", "데일리", "보통", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 8. 시그니처 혼동 후보 B: S Aren 비세토스 듀오 호보 · 블랙 ----------
        createSku(cheongdam, gangnam,
                "S Aren 비세토스 듀오 호보", "크로스바디",
                "/images/products/bag8.png",
                1290000,
                "블랙", "S", "비세토스 모노그램 캔버스 + 나파 가죽", 350,
                "지퍼형 심플 수납", "크로스바디/숄더", false, null,
                Attr.of("블랙", "쿨", "캔버스", "낮음", "중간", "정면중앙", "중간", "라운드", "소프트",
                        "스몰", "체인스트랩", "실버", "이브닝", "가벼움", "지퍼", "심플", "일반"),
                true, true, false);

        // ---------- 9. 기능 혼동 후보 A: M Stark 사이드 스터드 비세토스 백팩 · 꼬냑 (13인치까지만) ----------
        createSku(cheongdam, gangnam,
                "M Stark 사이드 스터드 비세토스 백팩", "백팩",
                "/images/products/bag9.png",
                1890000,
                "꼬냑", "M", "비세토스 모노그램 캔버스 + 가죽 트림", 650,
                "지퍼형 메인 수납 + 13인치 노트북 슬리브", "백팩(양쪽 숄더)", true, 13,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "골드", "오피스", "보통", "지퍼", "구획많음", "일반"),
                true, true, false);

        // ---------- 10. 기능 혼동 후보 B: M Aren ECONYL 가죽 백팩 · 그린 (스펙은 맞지만 오늘 재고 없음) ----------
        createSku(cheongdam, gangnam,
                "M Aren ECONYL 가죽 백팩", "백팩",
                "/images/products/bag10.png",
                1650000,
                "그린", "M", "ECONYL 재생나일론 + 가죽 트림", 600,
                "16인치 노트북 슬리브 + 다수 포켓", "백팩(양쪽 숄더)", true, 16,
                Attr.of("그린", "쿨", "패브릭", "중간", "낮음", "정면하단", "낮음", "사각", "소프트",
                        "미디움", "패브릭스트랩", "건메탈", "오피스", "보통", "지퍼", "구획많음", "일반"),
                false, true, true); // 청담 없음, 강남 있음(타 매장 재고 있음)

        // ---------- 11. 명확한 비적합 후보 A: 미니 Tracy 비세토스 레더 믹스 크로스바디 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "미니 Tracy 비세토스 레더 믹스 크로스바디", "크로스바디",
                "/images/products/bag11.png",
                1050000,
                "꼬냑", "미니", "비세토스 모노그램 캔버스 + 가죽 트림", 220,
                "플립형 심플 수납", "크로스바디", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "사각", "소프트",
                        "미니", "체인스트랩", "골드", "이브닝", "가벼움", "플립", "심플", "일반"),
                true, true, false);

        // ---------- 12. 명확한 비적합 후보 B: S Pina 비세토스 탬버린 백 · 꼬냑 ----------
        createSku(cheongdam, gangnam,
                "S Pina 비세토스 탬버린 백", "크로스바디",
                "/images/products/bag12.png",
                1690000,
                "꼬냑", "S", "비세토스 모노그램 캔버스", 280,
                "지퍼형 단일 수납", "크로스바디", false, null,
                Attr.of("꼬냑", "웜", "캔버스", "낮음", "높음", "정면중앙", "높음", "라운드", "하드",
                        "스몰", "체인스트랩", "골드", "이브닝", "가벼움", "지퍼", "심플", "일반"),
                false, true, false); // 재고 없음 (명확한 비적합 후보)

        // ---------- 고객 ----------
        // 고객은 전화번호를 자연 키로 upsert 한다. 신규로 만들어진 경우에만 아래 동의/장바구니
        // 초기 데이터를 넣어, 재기동할 때마다 같은 장바구니 항목이 다시 쌓이지 않게 한다.
        boolean kimIsNew = customerRepository.findByPhoneNumber("010-1234-5678").isEmpty();
        Customer kim = upsertCustomer("김세계", "010-1234-5678");
        boolean leeIsNew = customerRepository.findByPhoneNumber("010-9876-5432").isEmpty();
        Customer lee = upsertCustomer("이수현", "010-9876-5432");

        if (kimIsNew) {
            // ---------- 고객 동의 ----------
            consentRecordRepository.save(ConsentRecord.builder()
                    .customer(kim).status(ConsentStatus.AGREE)
                    .scope("장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인")
                    .consentedAt(now)
                    .build());

            // ---------- 장바구니: 세 페르소나가 공통으로 담는 원제품(SKU 1) ----------
            cartItemRepository.save(CartItem.builder()
                    .customer(kim).sku(s1).color(s1.getColor()).size(s1.getSize())
                    .savedAt(now.minusMinutes(5)).build());
        }

        // 이수현은 의도적으로 동의 기록을 남기지 않는다 -> "동의 필요" 차단 흐름 데모용.
        if (leeIsNew) {
            // 이수현 장바구니: 동의 전이므로 GET /api/cart 는 403
            cartItemRepository.save(CartItem.builder()
                    .customer(lee).sku(s1).color(s1.getColor()).size(s1.getSize())
                    .savedAt(now.minusHours(1)).build());
        }
    }

    private Store upsertStore(String name) {
        return storeRepository.findByName(name)
                .orElseGet(() -> storeRepository.save(Store.builder().name(name).build()));
    }

    private Customer upsertCustomer(String name, String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .map(existing -> {
                    existing.setName(name);
                    return existing;
                })
                .orElseGet(() -> customerRepository.save(
                        Customer.builder().name(name).phoneNumber(phoneNumber).build()));
    }

    private Sku createSku(Store cheongdam, Store gangnam,
                           String productName, String category, String imageUrl,
                           Integer price,
                           String color, String size, String materialText, Integer weightGrams,
                           String storageStructure, String wearStyle,
                           boolean laptopCompatible, Integer laptopMaxInch,
                           Attr attr,
                           boolean inStockAtCheongdam, boolean confirmed, boolean inStockAtGangnam) {
        Product product = productRepository.findByName(productName)
                .map(existing -> {
                    existing.setImageUrl(imageUrl);
                    existing.setCategory(category);
                    existing.setPrice(price);
                    return existing;
                })
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name(productName).imageUrl(imageUrl).category(category).price(price)
                        .build()));

        Sku sku = skuRepository.findByProductIdAndColorAndSize(product.getId(), color, size)
                .orElseGet(() -> skuRepository.save(Sku.builder()
                        .product(product).color(color).size(size)
                        .build()));
        sku.setMaterial(materialText);
        sku.setWeightGrams(weightGrams);
        sku.setStorageStructure(storageStructure);
        sku.setWearStyle(wearStyle);
        sku.setLaptopCompatible(laptopCompatible);
        sku.setLaptopMaxInch(laptopMaxInch);

        ProductAttribute attribute = productAttributeRepository.findBySkuId(sku.getId())
                .orElseGet(() -> productAttributeRepository.save(
                        ProductAttribute.builder().sku(sku).build()));
        applyAttribute(attribute, attr);

        LocalDateTime now = LocalDateTime.now();
        upsertInventory(sku, cheongdam, inStockAtCheongdam, inStockAtGangnam, confirmed, now);
        upsertInventory(sku, gangnam, inStockAtGangnam, inStockAtCheongdam, confirmed, now);

        return sku;
    }

    private void applyAttribute(ProductAttribute a, Attr attr) {
        a.setColorFamily(attr.colorFamily); a.setColorTone(attr.colorTone); a.setMaterial(attr.material);
        a.setGlossLevel(attr.glossLevel); a.setLogoVisibility(attr.logoVisibility);
        a.setLogoPosition(attr.logoPosition); a.setPatternDensity(attr.patternDensity);
        a.setSilhouette(attr.silhouette); a.setStructure(attr.structure); a.setSizeGrade(attr.sizeGrade);
        a.setStrapType(attr.strapType); a.setHardwareColor(attr.hardwareColor);
        a.setUsageContext(attr.usageContext); a.setWeightGrade(attr.weightGrade);
        a.setLockType(attr.lockType); a.setInternalStorageLevel(attr.internalStorageLevel);
        a.setHandleType(attr.handleType);
    }

    private void upsertInventory(Sku sku, Store store, boolean currentStoreInStock,
                                  boolean otherStoreInStock, boolean confirmed, LocalDateTime now) {
        Inventory inventory = inventoryRepository.findBySkuIdAndStoreId(sku.getId(), store.getId())
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder().sku(sku).store(store)
                                .currentStoreInStock(currentStoreInStock).otherStoreInStock(otherStoreInStock)
                                .restockPlanned(false).confirmed(confirmed).checkedAt(now)
                                .build()));
        inventory.setCurrentStoreInStock(currentStoreInStock);
        inventory.setOtherStoreInStock(otherStoreInStock);
        inventory.setRestockPlanned(false);
        inventory.setConfirmed(confirmed);
        inventory.setCheckedAt(now);
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
