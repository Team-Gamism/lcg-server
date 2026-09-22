# 아키텍처

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
