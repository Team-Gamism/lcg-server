# 서버 기반

## 채택한 구조

Kotlin + Spring Boot의 단일 배포 애플리케이션과 Gradle Kotlin DSL을 사용한다. 기본 패키지는 `com.lcg`다. 기능별 하위 폴더는 실제 클래스가 필요한 시점에 추가한다.

```text
build.gradle.kts
settings.gradle.kts
gradlew / gradlew.bat
gradle/wrapper/
src/main/kotlin/com/lcg/
  LcgServerApplication.kt
  domain/
    <feature>/
      entity/
      repository/
      presentation/
        controller/
        data/request/
        data/response/
      service/
        impl/
  global/
    config/
    exception/
    filter/
    redis/
src/main/resources/
  application.yml
src/test/kotlin/com/lcg/
  unit/
  integration/
```

[`cowork-server`의 `cowork-project`](https://github.com/team-cowork/cowork-server/tree/main/cowork-project/src/main/kotlin/com/cowork/project) 구조를 참고해 기능별 `entity` / `repository` / `presentation` / `service` 패키지와 공통 `global` 패키지를 사용한다. 서비스는 인터페이스와 `impl` 구현으로 구분하고 메서드 이름은 `execute`를 기본으로 한다. 읽기 서비스는 DB를 조회할 때 `@Transactional(readOnly = true)`, 쓰기 서비스는 `@Transactional`을 사용한다. 단일 배포 모놀리식은 유지하며 기능 간 테이블을 직접 수정하지 않는다.

## 구현 현황 (2026-09-21)

| 작업 | 구현한 내용 | 남은 내용 |
| --- | --- | --- |
| FND-01 | 빌드 버전·패키지 구조·JPA/Flyway 선택 | DataGSM SDK 호환성, D-01~11 정책 결정 |
| FND-02 | Gradle Wrapper, JDK 21 toolchain, 환경별 설정, 로컬 Compose, 실행 안내와 검증 완료 | 없음 |
| FND-03 | User/SchoolIdentity 엔티티·저장소, V1 마이그레이션, UUID·UTC·고유키·외래키·낙관적 버전 | 회원 상태·권한 모델, 이전 스키마 업그레이드 검증, 운영 마이그레이션 절차 |
| FND-04 | Redis 연결·타임아웃, 환경별 키·버전·세션 네임스페이스, TTL 검증 | 실제 세션 저장, 캐시 복구, 장애 처리, 분산 요청 합치기 |
| FND-05 | 상태 API, 로컬 OpenAPI, CORS·CSRF·기본 접근 제어, ProblemDetail·오류 코드·traceId | 페이지네이션, 요청 크기·빈도 제한, 실제 회원·리소스 권한 검사 |
| FND-06 | 미착수 | 영속 작업·Outbox 전체 |

버전은 JDK 21, Kotlin 2.3.21, Spring Boot 4.1.1, Gradle 9.7.1, PostgreSQL 17.11, Redis 8.2.9로 고정했다. Gradle 배포 체크섬과 Compose 이미지 digest도 기록했다. DataGSM OAuth SDK는 아직 의존성에 추가하지 않았으며 `AUTH-01`에서 검증한다.

현재 HTTP 성공 응답은 DTO, 오류 응답은 `ProblemDetail`이다. `cowork-project`의 패키지·서비스·DTO 작성 방식을 적용했고, 공통 라이브러리 `the-sdk`와 `CommonApiResponse`는 도입하지 않았다. 학교 OAuth SDK와는 별개의 라이브러리다. 상태 확인을 제외한 API는 인증이 필요하며 로그인 기능과 Redis 세션 저장은 아직 없다.

로컬 실행 절차는 [루트 README](../README.md)를 따른다. PostgreSQL은 `15432`, Redis는 `16379`를 사용한다. 기존 기본 포트의 서비스를 함께 실행할 수 있다.

마이그레이션은 현재 애플리케이션 시작 시 Flyway가 수행하고, JPA는 `ddl-auto: validate`로 스키마를 검증한다. 배포된 마이그레이션은 수정하지 않고 다음 버전을 추가한다. 운영 전에는 `FND-03`·`OPS-02`에서 실행 권한과 배포 절차를 확정한다.

검증 결과: `check bootJar`와 `integrationTest` 성공. API 계약 16개, 실제 PostgreSQL·Redis 통합 6개가 모두 통과했다. 빈 DB의 V1 생성, 재실행 시 적용할 마이그레이션 없음, 회원 UTC 저장·고유키·외래키, Redis TTL, 상태 API·OpenAPI를 확인했다. `prod` 프로필의 필수 설정 누락 시 JAR가 종료 코드 1로 실패하는 것도 확인했다. 원격 GitHub Actions 실행 결과는 아직 없다.

## TODO

- [ ] **FND-01 | P0 | 핵심 정책과 개발 계약 확정** — 선행: 없음.
  - 구현: [채택한 기술 방향과 결정 사항 D-01~11](00-requirements.md)의 채택안, 단계별 범위, 외부 승인 의존성을 기록한다. JDK·Kotlin·Spring Boot·빌드 도구·DataGSM SDK와 DB/Redis의 지원 버전 및 호환성을 착수 시 확인하고 고정한다.
  - 완료 기준: 인증·랭킹 구현을 막는 미결정 사항과 해결 담당/시점이 식별되고, 채택한 API·세션·회원 정책이 한 문서에 남는다.

- [x] **FND-02 | P0 | Kotlin + Spring Boot 프로젝트와 로컬 실행 환경** — 선행: `FND-01` 중 기술 기반 결정. 인증·랭킹 정책은 해당 기능 착수 전 계속 확정한다.
  - 구현: Gradle Kotlin DSL·Wrapper·JDK toolchain을 기본안으로 빌드 구성을 확정하고 서버·모듈·단위/통합 테스트 구조를 만든다. Kotlin/Spring 플러그인·의존성 버전, 환경별 설정, PostgreSQL/Redis 로컬 실행 설정, 비밀값 없는 설정 예시, 실행 안내를 만든다.
  - 완료 기준: 신규 체크아웃에서 안내된 명령으로 의존 서비스와 API가 실행된다. 설정 누락은 시작 시 명확히 실패하고 저장소에 실제 비밀값이 없다.

- [ ] **FND-03 | P0 | PostgreSQL 모델·마이그레이션·트랜잭션 규칙** — 선행: `FND-02`.
  - 구현: Spring Data JPA를 기본안으로 데이터 접근 방식과 마이그레이션 도구를 확정한다. 최초 회원 모델, 모듈별 마이그레이션 책임, 고유/외래키/인덱스, UTC 시간, 트랜잭션 경계와 낙관적 버전 또는 행 잠금 규칙을 정한다. 이후 도메인 스키마는 해당 기능과 함께 추가한다.
  - 완료 기준: 빈 DB 생성과 이전 버전에서 업그레이드가 재현된다. 회원 식별자 중복은 DB에서 거절된다. 운영 마이그레이션 실행 주체가 명확하다.

- [ ] **FND-04 | P0 | Redis 연결·키·실패 정책** — 선행: `FND-02`.
  - 구현: 환경별 키 접두사, TTL·직렬화 버전, 세션/캐시 네임스페이스, 타임아웃, 분산 환경에서의 요청 합치기 규칙을 만든다.
  - 완료 기준: 캐시 소실은 DB/API로 복구되고, 세션 저장소 장애는 인증을 우회하지 않는다. 락 사용 시 소유 토큰·만료를 검사하며 정합성은 DB가 최종 보장한다.

- [ ] **FND-05 | P0 | 공통 API·권한·오류 계약** — 선행: `FND-02`.
  - 구현: Spring MVC 기반 `/api/v1`, OpenAPI, 입력 검증, Spring의 `ProblemDetail`과 도메인 오류 코드/traceId, 페이지네이션, 요청 크기·빈도 제한을 구성한다. Spring Security를 통한 명시적 CORS, CSRF, 역할·리소스 권한 정책을 정한다. [Spring 오류 응답 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
  - 완료 기준: 400/401/403/404/409/429와 외부 서비스 장애 응답을 구분한다. 예외·민감한 학교 정보가 응답에 없고 익명/일반/소유자/운영자 권한 사례가 정의된다.

- [ ] **FND-06 | P2 | 영속 작업 실행과 도메인 이벤트 전달** — 선행: `FND-03~05`.
  - 구현: Spring 기반 작업 실행기 또는 작업 라이브러리를 선택하고, DB 작업/Outbox, 실행 임대·중복 방지·재시도 상한·실패 보관·재처리를 구현한다. 프로세스 내 스케줄링만으로 영속성을 보장한다고 가정하지 않는다. 외부 메시지 브로커는 초기 필수가 아니다.
  - 완료 기준: DB 커밋 후 프로세스가 죽어도 알림 이벤트를 재처리한다. 여러 인스턴스가 실행해도 참가 상태나 알림을 중복 생성하지 않는다. Riot 동기화의 초기 작업도 필요 시 이 기반으로 이관한다.

## 공통 응답 제안

목록에는 `items`, `nextCursor`(또는 일관된 페이지 방식)를 사용한다. 외부 데이터에는 `updatedAt`, `isStale`, 동기화 중인 경우 `syncStatus`를 붙인다. 갱신은 긴 동기 요청보다 `202 Accepted`와 작업 조회를 사용한다. 공개 DTO는 DB 엔티티나 DataGSM 원본 응답을 그대로 직렬화하지 않는다.
