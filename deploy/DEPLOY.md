# DEPLOY.md — 배포 가이드

가비아 클라우드(Ubuntu 24.04) 기준 배포 절차. 서버 한 대에 백엔드·DB·프론트를 모두 올린다.

## 구성

```
segue.asia (가비아 도메인)
        │
가비아 클라우드 서버 1대 (2vCore / 4GB / 100GB)
├─ nginx        :443   HTTPS. 프론트 정적 파일 + /api, /images 를 백엔드로 전달
├─ nginx        :80    HTTPS 로 301 리다이렉트만 담당
├─ Spring Boot  :8080  백엔드 (외부에 노출하지 않음)
└─ MySQL        :3306  DB (외부에 노출하지 않음)
```

프론트와 백엔드가 **같은 주소에서 서빙되므로 CORS 도, 혼합 콘텐츠(HTTPS 페이지에서 HTTP API
호출) 문제도 발생하지 않는다.**

보안그룹(방화벽)에서 **80(HTTP)·443(HTTPS)·22(SSH)만 연다.** 8080 과 3306 은 서버 내부 통신이므로
열지 않는다. 열면 백엔드와 DB 가 그대로 인터넷에 노출된다.

---

## 1. 서버 준비

```bash
apt update && apt upgrade -y
apt install -y openjdk-21-jdk git nginx mysql-server
systemctl enable --now mysql
```

Java 21 이 필요하다. Ubuntu 24.04 는 `openjdk-21-jdk` 가 기본이라 그대로 설치된다.

## 2. 데이터베이스

```bash
mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS segue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'segue'@'localhost' IDENTIFIED BY '<비밀번호>';
GRANT ALL PRIVILEGES ON segue.* TO 'segue'@'localhost';
FLUSH PRIVILEGES;
SQL
```

**`utf8mb4` 가 필수다.** 없으면 한글 제품명·고객명이 `????` 로 저장된다.

> 계정이 이미 있으면 `CREATE USER IF NOT EXISTS` 는 비밀번호를 바꾸지 않고 경고만 낸다.
> 비밀번호를 확실히 설정하려면 `ALTER USER 'segue'@'localhost' IDENTIFIED BY '<비밀번호>';` 를 쓴다.

## 3. 코드와 환경변수

```bash
cd /opt
git clone https://github.com/hackathon-segue/segue-backend.git
cd segue-backend

cat > .env <<'ENV'
DB_HOST=localhost
DB_PORT=3306
DB_NAME=segue
DB_USERNAME=segue
DB_PASSWORD=<비밀번호>
OPENAI_API_KEY=<키>
OPENAI_MODEL=gpt-4o-mini
SERVER_PORT=8080
ENV
chmod 600 .env
```

`.env` 는 저장소에 없다(`.gitignore`). OpenAI 키와 DB 비밀번호가 들어가므로 `chmod 600` 으로
소유자만 읽게 막는다.

## 4. 빌드

```bash
./mvnw -B package -DskipTests
```

메모리가 부족하면 `export MAVEN_OPTS="-Xmx1g"` 후 다시 실행한다.

## 5. 서비스 등록

```bash
cp deploy/segue.service /etc/systemd/system/segue.service
systemctl daemon-reload
systemctl enable segue
systemctl start segue
systemctl status segue --no-pager
```

`active (running)` 이면 성공. 터미널을 닫아도 계속 돌고, 서버가 재부팅되거나 프로세스가 죽어도
자동으로 다시 뜬다.

**`--spring.profiles.active=prod` 는 서비스 파일에 포함되어 있다.** 운영 프로필을 켜지 않으면
SQL 로그가 그대로 남아 고객 개인정보가 파일에 기록되고, CORS 가 전체 허용으로 열린다.

## 6. nginx

```bash
mkdir -p /var/www/segue
cp deploy/nginx-segue.conf /etc/nginx/sites-available/segue
ln -sf /etc/nginx/sites-available/segue /etc/nginx/sites-enabled/segue
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

`nginx -t` 가 `syntax is ok` / `test is successful` 를 내야 한다.

## 6-1. HTTPS (Let's Encrypt)

먼저 도메인의 DNS A 레코드가 서버 IP 를 가리키고 있어야 하고, 방화벽에 **443 이 열려 있어야**
한다. 둘 중 하나라도 안 되어 있으면 certbot 의 도메인 소유 확인이 실패한다.

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d segue.asia -d www.segue.asia --redirect --agree-tos -m <이메일>
```

`--redirect` 가 HTTP 로 들어온 요청을 HTTPS 로 301 시키는 설정까지 넣어 준다.

```bash
curl -sI http://segue.asia | head -1     # 301
curl -sI https://segue.asia | head -1    # 200
certbot certificates                     # 만료일 확인
```

> **certbot 은 `/etc/nginx/sites-available/segue` 를 직접 고친다.** 443 블록과 인증서 경로가
> 그 파일에 추가되므로, 이후 재배포할 때 `deploy/nginx-segue.conf` 를 다시 복사하면 TLS 설정이
> 통째로 사라진다. nginx 설정을 바꿔야 하면 서버의 파일을 직접 편집한다.

인증서는 90일짜리이고 `certbot.timer` 가 자동 갱신한다 (`systemctl status certbot.timer`).

**HTTPS 를 적용했으면 `application-prod.properties` 의 `cors.allowed-origins` 도 `https://` 주소여야
한다.** 값이 어긋나면 브라우저 요청만 403 이 되는데, `curl` 은 `Origin` 헤더를 붙이지 않아 정상으로
보인다. 아래 8번의 Origin 포함 확인을 반드시 거칠 것.

## 7. 프론트 배포

프론트 빌드 결과물(`dist/` 또는 `build/web/`)의 **내용물**을 `/var/www/segue/` 에 넣는다.

```bash
# 로컬에서
scp -r build/web/* root@segue.asia:/var/www/segue/
```

프론트는 API 주소를 빌드 시점에 주입한다. **반드시 `https://` 로 빌드해야 한다.** HTTPS 페이지에서
HTTP API 를 호출하면 브라우저가 혼합 콘텐츠로 차단한다.

```bash
flutter build web --dart-define=API_BASE_URL=https://segue.asia --dart-define=APP_ENV=prod
```

> 재배포 후에는 **하드 리프레시**(`Cmd/Ctrl + Shift + R`)가 필요하다. 서비스 워커가 이전 파일을
> 캐싱하고 있어 그냥 새로고침하면 옛 빌드가 그대로 뜬다.

## 8. 확인

```bash
curl -s https://segue.asia/api/products | head -c 120
```

`{"id":1,...}` 로 시작하는 제품 12개가 나오면 정상이다.

**여기서 끝내면 안 된다.** 위 요청에는 `Origin` 헤더가 없어서 CORS 설정이 틀려 있어도 200 이
나온다. 브라우저와 같은 조건으로 한 번 더 확인한다.

```bash
# 허용된 오리진 -> 200
curl -s -o /dev/null -w '%{http_code}\n' -H 'Origin: https://segue.asia' \
  https://segue.asia/api/products

# 허용되지 않은 오리진 -> 403 (이게 200 이면 CORS 가 전체 허용 상태다)
curl -s -o /dev/null -w '%{http_code}\n' -H 'Origin: https://evil.example.com' \
  https://segue.asia/api/products
```

앞이 `200`, 뒤가 `403` 이어야 한다. 앞이 403 이면 `cors.allowed-origins` 가 실제 서비스 주소와
어긋난 것이고, 이 상태에서는 **화면에서 로그인이 되지 않는다.**

---

## 운영

| 목적 | 명령 |
| --- | --- |
| 재시작 | `systemctl restart segue` |
| 상태 확인 | `systemctl status segue` |
| 로그 보기 | `tail -f /var/log/segue.log` |
| 요청 기록 | `tail -f /opt/segue-backend/logs/access_log.$(date +%Y-%m-%d).log` |

**코드 업데이트**

```bash
cd /opt/segue-backend && git pull && ./mvnw -B package -DskipTests && systemctl restart segue
```

**시연 전 재기동을 권장한다.** 재기동하면 시드 계정의 동의 상태가 초기값으로 돌아간다
(김세계=동의 완료, 이수현=미동의). 테스트 중 동의 상태를 바꿔놨어도 원상복구된다.

---

## 알아둘 것

**빈 DB 로 처음 기동하면 `DataLoader` 가 제품 12개·SKU·재고·테스트 계정을 자동으로 넣는다.**
따로 데이터를 넣을 필요가 없다. 두 번째 기동부터는 자연 키로 찾아 갱신하므로 ID 가 바뀌지 않고
중복도 생기지 않는다.

**기동이 중간에 실패하면 ID 가 밀릴 수 있다.** 롤백돼도 auto_increment 는 되돌아가지 않기
때문이다. 문서와 데모가 SKU 1 을 원제품으로 잡고 있으므로, ID 가 1 부터 시작하지 않으면 아래로
초기화한다(시드 데이터만 있을 때에 한한다. 시연이 시작된 뒤에는 실제 데이터가 쌓이므로 하면 안 된다).

```bash
systemctl stop segue
mysql segue <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE consultation_result;
TRUNCATE TABLE cart_item;
TRUNCATE TABLE customer_consent;
TRUNCATE TABLE inventory;
TRUNCATE TABLE product_attribute;
TRUNCATE TABLE sku;
TRUNCATE TABLE product;
TRUNCATE TABLE customer;
TRUNCATE TABLE store;
SET FOREIGN_KEY_CHECKS=1;
SQL
systemctl start segue
```

**재고 확인 시각은 한 시간마다 자동 갱신된다.** `checked_at` 이 신선도 기준(12시간)을 넘기면
모든 재고가 미확인으로 판정되어 네 가지 결과가 전부 추가 상담으로 수렴하는데, 이 값은 기동할
때만 갱신되므로 서버를 오래 켜두면 문제가 된다. `InventoryFreshnessScheduler` 가 이를 막는다.

**테스트 계정** — 비밀번호는 둘 다 `segue1234`

| 이메일 | 전화번호 | 용도 |
| --- | --- | --- |
| `kim@segue.test` | `010-1234-5678` | 기본 시연용 (동의 완료) |
| `lee@segue.test` | `010-9876-5432` | 동의 차단(403) 흐름 시연용 |
