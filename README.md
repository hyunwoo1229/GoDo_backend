# GoDo Backend

드론 촬영 기록 플랫폼 백엔드

## 기술 스택
- Spring Boot 3.5.13
- Java 17
- MySQL 8.0
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

### 환경변수 설정
`.env.example`을 복사하여 `.env` 생성:

```bash
cp .env.example .env
# .env 편집: ORACLE_ACCESS_KEY, ORACLE_SECRET_KEY, ADMIN_PASSWORD
```

### MySQL 실행

```bash
docker compose up -d mysql
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 프로파일은 `local`, 서버는 `http://localhost:8080`에서 기동됩니다.

### Swagger UI

- `http://localhost:8080/swagger-ui.html`

## 프로파일

- `local` — 로컬 개발 (기본값)
- `prod` — 배포 환경 (환경변수로 DB 접속 정보 주입)

```bash
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/godo-0.0.1-SNAPSHOT.jar
```

## 빌드 & 도커

```bash
./gradlew bootJar
docker build -t godo:latest .
```
