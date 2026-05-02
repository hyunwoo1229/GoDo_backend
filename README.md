# GoDo Backend

드론 촬영 기록 플랫폼 백엔드

## 기술 스택
- Spring Boot 3.5.13
- Java 17
- MySQL 8.0 (로컬 개발)
- Oracle Autonomous Database (운영)
- Spring Security (Basic Auth)
- Oracle Cloud Object Storage (S3 호환)
- FFmpeg (썸네일 생성)

## 아키텍처
```
Client (Vite/React, :5173)
  ↓ Basic Auth (업로드/삭제 시)
Spring Boot API (:8080)
  ├→ MySQL (:3306)
  └→ Oracle Object Storage (S3 호환)
```

## 인증
- 조회 API (GET): 누구나 접근 가능
- 업로드/삭제 API: Basic Auth 필요 (관리자 전용)
- 관리자 계정: 환경변수 `ADMIN_PASSWORD`로 설정

## 실행 방법

### 사전 준비
1. Java 17 설치
2. Docker 설치
3. FFmpeg 설치 (`brew install ffmpeg`)

### 로컬 개발 (Docker MySQL)

1. `.env.example`을 복사하여 `.env` 생성

   ```bash
   cp .env.example .env
   # .env 편집: ORACLE_ACCESS_KEY, ORACLE_SECRET_KEY, ADMIN_PASSWORD
   ```

2. MySQL 컨테이너 실행

   ```bash
   docker compose up -d mysql
   ```

3. IntelliJ에서 `GodoApplication` 실행 (기본 profile: `local`)
   또는 CLI:

   ```bash
   ./gradlew bootRun
   ```

서버는 `http://localhost:8080`에서 기동됩니다.

### 운영 환경 (Oracle Autonomous DB)

1. `wallet/` 폴더에 Oracle Wallet 파일 배치 (`tnsnames.ora`, `sqlnet.ora`, `cwallet.sso` 등)
2. `.env.prod` 파일에 환경변수 설정 (`SPRING_PROFILES_ACTIVE=prod`, `TNS_ADMIN`, `DB_PASSWORD`, `ORACLE_ACCESS_KEY`, `ORACLE_SECRET_KEY`, `ADMIN_PASSWORD`)
3. IntelliJ에서 `GodoApplication-prod` 실행 (profile: `prod`, EnvFile 플러그인으로 `.env.prod` 로드)
   또는 CLI:

   ```bash
   set -a; source .env.prod; set +a
   ./gradlew bootRun --args='--spring.profiles.active=prod'
   ```

### Swagger UI

- `http://localhost:8080/swagger-ui.html`

## 프로파일

- `local` — 로컬 개발 (기본값, MySQL)
- `prod` — 운영 환경 (Oracle Autonomous DB, Wallet 필요)

## 빌드 & 도커

```bash
./gradlew bootJar
docker build -t godo:latest .
```
