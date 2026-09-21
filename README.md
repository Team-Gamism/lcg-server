# LCG Server

**LCG (League of Legend Champions GSM)** 서버 프로젝트입니다.

요구사항 분석과 우선순위별 구현 계획은 [todo/README.md](todo/README.md)를 참고하세요.

## 기술 구성

- JDK 21, Kotlin 2.3.21, Spring Boot 4.1.1
- PostgreSQL 17, Redis 8
- Gradle Kotlin DSL, Flyway, Spring Data JPA

Kotlin 패키지는 [cowork-server의 cowork-project](https://github.com/team-cowork/cowork-server/tree/main/cowork-project/src/main/kotlin/com/cowork/project)를 참고해 기능별 `domain`과 공통 `global`로 구성합니다. 서비스는 인터페이스와 `impl` 구현의 `execute()`로 나눕니다.

```text
src/main/kotlin/com/lcg/
├─ domain/<feature>/
│  ├─ entity/
│  ├─ repository/
│  ├─ presentation/controller/
│  ├─ presentation/data/request|response/
│  └─ service/impl/
└─ global/
   ├─ config/
   ├─ exception/
   ├─ filter/
   └─ redis/
```

## 로컬 실행

JDK 21과 실행 중인 Docker Desktop이 필요합니다. `JAVA_HOME` 또는 IDE의 Gradle JVM을 JDK 21로 설정하세요. 첫 빌드에는 Gradle과 의존성 다운로드를 위한 네트워크가 필요합니다.

```powershell
docker compose up -d --wait
$env:SPRING_PROFILES_ACTIVE = "local"
./gradlew.bat bootRun
```

macOS/Linux에서는 `SPRING_PROFILES_ACTIVE=local bash ./gradlew bootRun`을 사용합니다. 로컬 DB는 `localhost:15432`, Redis는 `localhost:16379`에 연결합니다. Compose와 `local` 설정의 비밀번호는 로컬 개발 전용 예시입니다.

서버가 실행되면 다음 주소를 사용할 수 있습니다.

- API 확인: `GET http://localhost:8080/api/v1/system/ping`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 준비 상태: `http://localhost:8080/actuator/health/readiness`

API 계약 테스트와 통합 테스트는 각각 다음 명령으로 실행합니다. 통합 테스트는 실행 중인 로컬 DB·Redis를 사용하며 테스트 데이터는 트랜잭션 롤백 또는 테스트 키 삭제로 정리합니다.

```powershell
./gradlew.bat test
./gradlew.bat integrationTest
```

CI와 동일한 검증·패키징 명령은 `./gradlew.bat check integrationTest bootJar`입니다. 결과 JAR는 `build/libs/lcg-server-0.0.1-SNAPSHOT.jar`에 생성됩니다.

## 현재 구현 범위

서버 실행 기반, 상태 API, JPA·Flyway의 최초 회원/학교 식별 스키마, Redis 연결, 공통 오류 처리와 Spring Security 기본 규칙까지 구현했습니다. 실제 로그인과 DataGSM SDK 연동은 다음 단계입니다. 성공 응답은 DTO를 반환하며 오류는 `ProblemDetail`의 `code`, `traceId`와 HTTP 상태로 구분합니다. 응답 헤더 `X-Request-ID`를 로그 조회에 사용할 수 있습니다.

상태 API와 로컬 문서 외의 API는 인증이 필요합니다. `local` 프로필에서만 Swagger를 공개하며, liveness와 readiness는 저장소 상세 정보를 반환하지 않습니다. 진행 상황과 남은 작업은 [서버 기반 TODO](todo/01-foundation.md)를 참고하세요.

운영 환경 변수의 형식은 [.env.example](.env.example)을 참고하세요. 해당 파일의 값은 예시이며 Spring이 자동으로 읽지 않습니다.
