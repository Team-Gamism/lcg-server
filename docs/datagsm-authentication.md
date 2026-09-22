# DataGSM 인증

## 로그인 실행

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

## API

| API | 용도 |
| --- | --- |
| `GET /api/v1/auth/school/login` | DataGSM 로그인으로 이동 |
| `GET /api/v1/auth/school/callback` | 서버에서 코드 교환 및 LCG 세션 발급 |
| `GET /api/v1/auth/csrf` | 변경 요청용 `headerName`, `token` 조회 |
| `GET /api/v1/me` | 본인의 학번 이름, Riot ID, 포지션, 소개와 인증 정보 조회 |
| `PATCH /api/v1/me` | Riot ID(`gameName#tagLine`), 주/부 포지션, 소개 수정 |
| `POST /api/v1/auth/logout` | 현재 LCG 세션 종료 |
| `POST /api/v1/auth/logout-all` | 기존 모든 LCG 세션의 다음 요청 차단 |

## 클라이언트와 보안

프론트의 API 호출은 `credentials: "include"`를 사용합니다. 로그인 후 CSRF 토큰을 새로 받아 변경 요청의 `X-CSRF-TOKEN` 헤더에 넣습니다. 운영은 같은 사이트의 HTTPS 웹/API, 정확한 `CORS_ALLOWED_ORIGINS`, HttpOnly/Secure/SameSite=Lax 쿠키를 전제로 합니다. 로컬에서만 Secure를 해제합니다.

로그인 때 DataGSM의 `studentNumber`와 `name`을 조합한 `학번 이름`을 본인 프로필에 갱신합니다. Riot ID는 형식만 확인한 미검증 표시값이며, 이후 Riot 계정 등록에서 소유 여부를 검증합니다. 다른 회원에게 프로필을 공개하는 API와 공개 범위 설정은 아직 없습니다.

## 세션

세션은 30분 미사용 또는 로그인 후 최대 8시간에 만료됩니다. 매 인증 요청에서 DB 회원 상태·학교 자격·세션 버전을 검사합니다. 전체 로그아웃은 버전을 증가시켜 기존 세션을 거부하고, Redis의 남은 세션 데이터는 만료 시 제거됩니다. 운영자 권한은 LCG DB에서 별도 관리하며 학교의 `role`에서 가져오지 않습니다.
