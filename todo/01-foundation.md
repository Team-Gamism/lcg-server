# 서버 기반

## 구조 제안

Kotlin + Spring Boot의 단일 배포 애플리케이션으로 시작하며 Gradle Kotlin DSL을 제안한다. 아래 `<base-package>`는 `FND-02`에서 정할 패키지 경로다.

```text
build.gradle.kts
settings.gradle.kts
gradlew / gradlew.bat
gradle/wrapper/
src/main/kotlin/<base-package>/
  LcgServerApplication.kt
  modules/
    auth/ users/ riot/ matches/ rankings/
    statistics/ parties/ inhouses/ community/ notifications/
  infrastructure/   # DB, Redis, 외부 HTTP, 작업 실행, 관측
  shared/           # 최소 공통 타입·오류·시간·현재 사용자
src/main/resources/
  application.yml
src/test/kotlin/<base-package>/
  unit/
  integration/
```

모듈 내부를 API(Controller) / Application / Domain / Infrastructure 책임으로 구분하되 초기부터 각 계층을 별도 서비스로 배포하지 않는다. 모듈 간에는 서비스 계약/이벤트를 사용하고 다른 모듈의 테이블을 직접 수정하지 않는다. 홈 조합은 읽기 전용 서비스를 사용한다.

## TODO

- [ ] **FND-01 | P0 | 핵심 정책과 개발 계약 확정** — 선행: 없음.
  - 구현: [채택한 기술 방향과 결정 사항 D-01~11](00-requirements.md)의 채택안, 단계별 범위, 외부 승인 의존성을 기록한다. JDK·Kotlin·Spring Boot·빌드 도구·DataGSM SDK와 DB/Redis의 지원 버전 및 호환성을 착수 시 확인하고 고정한다.
  - 완료 기준: 인증·랭킹 구현을 막는 미결정 사항과 해결 담당/시점이 식별되고, 채택한 API·세션·회원 정책이 한 문서에 남는다.

- [ ] **FND-02 | P0 | Kotlin + Spring Boot 프로젝트와 로컬 실행 환경** — 선행: `FND-01`.
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
