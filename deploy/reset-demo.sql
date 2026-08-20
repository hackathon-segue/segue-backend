-- 시연으로 쌓인 데이터를 페르소나 시연 직전 상태로 되돌린다.
--
-- 사용법 (서버 중지 불필요):
--   mysql segue < deploy/reset-demo.sql
--
-- 서버를 멈출 필요가 없다. 이 스크립트가 DataLoader 의 시드 상태를 그대로 재현하므로
-- systemctl stop/start 없이 실행해도 되고, 촬영 중간에 돌려도 다운타임이 없다.
--
-- 왜 INSERT 가 필요한가:
--   DataLoader 의 장바구니 시딩은 "고객 row 가 새로 생길 때만" 동작한다(DataLoader.java:213).
--   cart_item 을 지워도 고객 row 는 남으므로, 재기동해도 장바구니는 복원되지 않는다.
--   그래서 삭제만 하면 장바구니가 빈 상태로 시연이 시작된다. 아래 INSERT 가 그 복원을 대신한다.
--
-- 건드리지 않는 것: store / product / sku / product_attribute / inventory
--   DataLoader 가 매 기동 코드값으로 upsert 하므로 이미 항상 시드 상태다.

START TRANSACTION;

-- ---------- 1. 시연 중 쌓인 데이터 삭제 ----------
DELETE FROM consultation_result;
DELETE FROM cart_item;
DELETE FROM customer_consent;

-- 시연 중 회원가입 흐름을 찍었다면 생긴 계정. 시드 2명만 남긴다.
DELETE FROM customer WHERE phone_number NOT IN ('010-1234-5678', '010-9876-5432');

-- ---------- 2. 시드 장바구니 복원 (페르소나 1~5 공통 원제품 SKU) ----------
INSERT INTO cart_item (customer_id, sku_id, color, size, saved_at)
SELECT c.id, s.id, s.color, s.size, NOW() - INTERVAL 5 MINUTE
  FROM customer c
  JOIN sku s ON s.color = '꼬냑' AND s.size = 'M'
  JOIN product p ON p.id = s.product_id AND p.name = 'M Diamond 비세토스 레더 믹스'
 WHERE c.phone_number = '010-1234-5678';

INSERT INTO cart_item (customer_id, sku_id, color, size, saved_at)
SELECT c.id, s.id, s.color, s.size, NOW() - INTERVAL 60 MINUTE
  FROM customer c
  JOIN sku s ON s.color = '꼬냑' AND s.size = 'M'
  JOIN product p ON p.id = s.product_id AND p.name = 'M Diamond 비세토스 레더 믹스'
 WHERE c.phone_number = '010-9876-5432';

-- ---------- 3. 동의 상태 복원 ----------
-- 김세계만 동의 완료. 이수현은 기록이 없어야 403 차단 흐름을 찍을 수 있다.
INSERT INTO customer_consent (customer_id, status, scope, consented_at)
SELECT c.id, 'AGREE', '장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인', NOW()
  FROM customer c
 WHERE c.phone_number = '010-1234-5678';

COMMIT;

-- ---------- 4. 확인 ----------
SELECT '상담결과 (0이어야 함)' AS 항목, COUNT(*) AS 값 FROM consultation_result
UNION ALL SELECT '장바구니 (2여야 함)', COUNT(*) FROM cart_item
UNION ALL SELECT '동의기록 (1이어야 함)', COUNT(*) FROM customer_consent
UNION ALL SELECT '고객 (2여야 함)', COUNT(*) FROM customer
UNION ALL SELECT '제품 (12여야 함)', COUNT(*) FROM product;
