# godo

Spring Boot 기반 미디어 업로드/관리 서비스.

## 요구 사항

- JDK 17
- Docker & Docker Compose
- FFmpeg (도커 이미지에는 포함되어 있음)

## 빠른 시작

### 1. 환경 변수 설정

```bash
cp .env.example .env
# .env 편집하여 실제 값 입력
```

### 2. MySQL 실행

```bash
docker compose up -d mysql
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 프로파일은 `local`이며, `application-local.yml`의 설정을 사용합니다.
서버는 `http://localhost:8080` 에서 기동됩니다.

### 4. Swagger UI

- `http://localhost:8080/swagger-ui.html`

## 빌드 & 도커

```bash
./gradlew bootJar
docker build -t godo:latest .
```

## 프로파일

- `local` — 로컬 개발 (기본값)
- `prod` — 배포 환경 (환경변수로 DB 접속 정보 주입)

프로파일 전환:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/godo-0.0.1-SNAPSHOT.jar
```
