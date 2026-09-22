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

서버 기반과 DataGSM SDK 1.6.0 기반 PKCE 로그인, 재학생 자격 검사, Redis 세션, 내 프로필 조회·수정, 현재/전체 세션 로그아웃을 구현했습니다. 성공 응답은 DTO를 반환하며 오류는 `ProblemDetail`의 `code`, `traceId`와 HTTP 상태로 구분합니다. 응답 헤더 `X-Request-ID`를 로그 조회에 사용할 수 있습니다.

상태·로그인 시작·콜백·CSRF 토큰 API는 공개됩니다. 나머지 API는 인증이 필요하며 `local` 프로필에서만 Swagger를 공개합니다. liveness와 readiness는 저장소 상세 정보를 반환하지 않습니다. 진행 상황은 [서버 기반 TODO](todo/01-foundation.md)와 [인증 TODO](todo/02-auth-users.md)를 참고하세요.

운영 환경 변수의 형식은 [.env.example](.env.example)을 참고하세요. 해당 파일의 값은 예시이며 Spring이 자동으로 읽지 않습니다.

## DataGSM 로그인 실행

기본값은 `DATAGSM_ENABLED=false`입니다. 자격 증명 없이도 서버와 모의 DataGSM 테스트를 실행할 수 있으며, 비활성 상태의 로그인 시작 요청은 `503 SERVICE_UNAVAILABLE`을 반환합니다.

1. [DataGSM 클라이언트 관리](https://www.datagsm.kr/clients)에서 개발 클라이언트를 등록합니다. 권한은 `datagsm:self_read`, callback은 `http://localhost:8080/api/v1/auth/school/callback`으로 설정합니다.
2. IDE 실행 환경이나 로컬 셸에 아래 변수를 지정하고 서버를 실행합니다. 실제 secret은 저장소에 넣지 않습니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DATAGSM_ENABLED = "true"
$env:DATAGSM_CLIENT_ID = "발급받은-client-id"
$env:DATAGSM_CLIENT_SECRET = "발급받은-client-secret"
./gradlew.bat bootRun
```

3. 브라우저에서 `http://localhost:8080/api/v1/auth/school/login`을 엽니다. 성공하면 LCG 세션 쿠키가 발급되고 기본값인 `http://localhost:3000`으로 이동합니다. 이동 주소는 `LOGIN_SUCCESS_URI`로 고정하며 요청의 `returnUrl`은 사용하지 않습니다.

SDK 생성자는 secret을 필수로 요구하지만, 실제 PKCE 코드 교환에는 `code_verifier`만 전송합니다. 학교 access/refresh token은 DB·Redis·브라우저에 저장하지 않습니다. 개발/운영 클라이언트 발급과 실제 학교 계정의 브라우저 로그인은 별도로 확인해야 합니다.

| API | 용도 |
| --- | --- |
| `GET /api/v1/auth/school/login` | DataGSM 로그인으로 이동 |
| `GET /api/v1/auth/school/callback` | 서버에서 코드 교환 및 LCG 세션 발급 |
| `GET /api/v1/auth/csrf` | 변경 요청용 `headerName`, `token` 조회 |
| `GET /api/v1/me` | 본인의 학번 이름, Riot ID, 포지션, 소개와 인증 정보 조회 |
| `PATCH /api/v1/me` | Riot ID(`gameName#tagLine`), 주/부 포지션, 소개 수정 |
| `POST /api/v1/auth/logout` | 현재 LCG 세션 종료 |
| `POST /api/v1/auth/logout-all` | 기존 모든 LCG 세션의 다음 요청 차단 |

프론트의 API 호출은 `credentials: "include"`를 사용합니다. 로그인 후 CSRF 토큰을 새로 받아 변경 요청의 `X-CSRF-TOKEN` 헤더에 넣습니다. 운영은 같은 사이트의 HTTPS 웹/API, 정확한 `CORS_ALLOWED_ORIGINS`, HttpOnly/Secure/SameSite=Lax 쿠키를 전제로 합니다. 로컬에서만 Secure를 해제합니다.

로그인 때 DataGSM의 `studentNumber`와 `name`을 조합한 `학번 이름`을 본인 프로필에 갱신합니다. Riot ID는 형식만 확인한 미검증 표시값이며, 이후 Riot 계정 등록에서 소유 여부를 검증합니다. 다른 회원에게 프로필을 공개하는 API와 공개 범위 설정은 아직 없습니다.

세션은 30분 미사용 또는 로그인 후 최대 8시간에 만료됩니다. 매 인증 요청에서 DB 회원 상태·학교 자격·세션 버전을 검사합니다. 전체 로그아웃은 버전을 증가시켜 기존 세션을 거부하고, Redis의 남은 세션 데이터는 만료 시 제거됩니다. 운영자 권한은 LCG DB에서 별도 관리하며 학교의 `role`에서 가져오지 않습니다.
