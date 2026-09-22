# 현재 구현 범위

서버 기반과 DataGSM SDK 1.6.0 기반 PKCE 로그인, 재학생 자격 검사, Redis 세션, 내 프로필 조회·수정, 현재/전체 세션 로그아웃을 구현했습니다. 성공 응답은 DTO를 반환하며 오류는 `ProblemDetail`의 `code`, `traceId`와 HTTP 상태로 구분합니다. 응답 헤더 `X-Request-ID`를 로그 조회에 사용할 수 있습니다.

상태·로그인 시작·콜백·CSRF 토큰 API는 공개됩니다. 나머지 API는 인증이 필요하며 `local` 프로필에서만 Swagger를 공개합니다. liveness와 readiness는 저장소 상세 정보를 반환하지 않습니다. 진행 상황은 [서버 기반 TODO](../todo/01-foundation.md)와 [인증 TODO](../todo/02-auth-users.md)를 참고하세요.
