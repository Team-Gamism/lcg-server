# 개발 환경과 로컬 실행

## 사전 요구 사항

JDK 21과 실행 중인 Docker Desktop이 필요합니다. `JAVA_HOME` 또는 IDE의 Gradle JVM을 JDK 21로 설정하세요. 첫 빌드에는 Gradle과 의존성 다운로드를 위한 네트워크가 필요합니다.

## 실행

```powershell
docker compose up -d --wait
$env:SPRING_PROFILES_ACTIVE = "local"
./gradlew.bat bootRun
```

macOS/Linux에서는 다음 명령을 사용합니다.

```bash
SPRING_PROFILES_ACTIVE=local bash ./gradlew bootRun
```

로컬 DB는 `localhost:15432`, Redis는 `localhost:16379`에 연결합니다. Compose와 `local` 설정의 비밀번호는 로컬 개발 전용 예시입니다.

서버가 실행되면 다음 주소를 사용할 수 있습니다.

- API 확인: `GET http://localhost:8080/api/v1/system/ping`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 준비 상태: `http://localhost:8080/actuator/health/readiness`

## 테스트와 빌드

API 계약 테스트와 통합 테스트는 각각 다음 명령으로 실행합니다. 통합 테스트는 실행 중인 로컬 DB·Redis를 사용하며 테스트 데이터는 트랜잭션 롤백 또는 테스트 키 삭제로 정리합니다.

```powershell
./gradlew.bat test
./gradlew.bat integrationTest
```

CI와 동일한 검증·패키징 명령은 다음과 같습니다.

```powershell
./gradlew.bat check integrationTest bootJar
```

결과 JAR는 `build/libs/lcg-server-0.0.1-SNAPSHOT.jar`에 생성됩니다.
