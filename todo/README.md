# LCG 서버 구현 TODO

프로젝트명: **LCG (League of Legend Champions GSM)**.

작성일: 2026-09-21. 사용자 제공 서비스 기획서 1~25절과 공식 외부 API 문서를 분석했다. 현재 저장소에는 기획·TODO 문서와 저장소 설정만 있으며 서버 구현은 없다. 이 폴더는 **구현 계획**이며 체크되지 않은 항목은 모두 미구현이다.

2026-09-21 기술 선택을 반영해 **Kotlin + Spring Boot + PostgreSQL + Redis, Modular Monolith** 구성을 기준으로 한다. 학교 OAuth는 DataGSM 공식 Java/Kotlin SDK를 사용하며, 채택 버전과 실제 계약의 호환성은 구현 전에 검증한다. 실시간 통신 방식은 P2의 `RT-01`에서 결정하고 MVP에는 소켓 도입을 요구하지 않는다. [채택한 기술 방향](00-requirements.md#채택한-기술-방향)을 제외한 API 경로, 엔티티, 운영 수치와 정책 기본안은 설계 제안이며 확정 명세가 아니다. 프론트엔드 구현은 범위에서 제외하고 서버가 제공할 계약만 정리한다.

## 우선순위

| 등급 | 의미 | 목표 |
| --- | --- | --- |
| P0 | 먼저 결정하거나 구축할 기반 | 인증·데이터·외부 API·운영의 선행 조건 |
| P1 | Phase 1 MVP 필수 | 학교 로그인 → 프로필 → Riot 등록 → 전적·학교 랭킹 → 커뮤니티 |
| P2 | Phase 2 핵심 확장 | 파티·내전·실시간 상태·기본 알림 |
| P3 | Phase 3 이용 활성화 | 학교 통계·Weekly·클립·인기글·관심 알림·활동 |
| P4 | 승인·운영 경험에 의존하는 확장 | RSO 정식 연동·Tournament API·LCG CUP |

P0는 모두 첫날 끝내라는 뜻이 아니다. `OPS-02`처럼 **공개 출시 전** 완료할 P0도 있다. 같은 등급에서는 선행 작업 순서를 따른다. P4 기능이라도 외부 승인 가능성 확인은 P0에서 시작한다.

## 문서 안내

| 문서 | 내용 |
| --- | --- |
| [00-requirements.md](00-requirements.md) | 원문 요구사항 대응표, 정책 기본안, 미결정 사항 |
| [01-foundation.md](01-foundation.md) | 프로젝트 구조, DB, Redis, 공통 API, 작업 실행 기반 |
| [02-auth-users.md](02-auth-users.md) | DataGSM OAuth 명세와 인증·프로필 TODO |
| [03-riot-matches-rankings.md](03-riot-matches-rankings.md) | 계정 등록, Riot 연동 계층, 전적, 학교 랭킹 |
| [04-community-home.md](04-community-home.md) | 게시글·댓글·추천·신고·클립·홈 |
| [05-parties-realtime.md](05-parties-realtime.md) | 듀오·5인 모집, 참가 경쟁 처리, P2 상태 전달 방식 선택 |
| [06-inhouses.md](06-inhouses.md) | 내전 모집·팀 구성·결과·동의 |
| [07-notifications-statistics-social.md](07-notifications-statistics-social.md) | 알림·통계·Weekly·관심 모드·차단·활동 |
| [08-riot-production-tournament.md](08-riot-production-tournament.md) | RSO 전환·Tournament API·LCG CUP |
| [09-quality-operations.md](09-quality-operations.md) | 단계별 검증, 배포·관측·복구·출시 조건 |

각 체크 항목에 작업 ID, 우선순위, 선행 작업, 구현 범위, 완료 기준을 적었다. 이후 이슈로 옮길 때 ID를 유지하고, 구현·검증을 끝낸 항목만 체크한다.

## 권장 구현 순서

1. `FND-01`에서 대상 회원, 계정 소유 확인, 세션 방식, 게임 모드 의미를 먼저 정한다. `RIOT-01`의 키·출시 조건 조사도 시작한다.
2. `FND-02~05`, `OPS-01`로 서버·DB·Redis·공통 규칙과 CI를 준비한다.
3. `AUTH-01~05`, `USER-01~03`으로 학교 로그인, 재로그인, 프로필·공개 범위를 완성한다.
4. `RIOT-02~05`, `MAT-01~03`, `RANK-01~02`로 Riot 등록·조회·학교 랭킹을 연결한다. 랭킹 출시에는 `D-02` 해결이 필요하다.
5. `COM-01~04`, `HOME-01`, `USER-04`와 `QA-01~02`, `OPS-02~03`으로 MVP를 검증한다. 장기 학교 토큰 보관이 필요하다면 `AUTH-06`도 포함한다.
6. `FND-06`, 파티·내전, `RT-01~02`, `NTF-01~02`, `QA-03`으로 Phase 2를 완성한다. `RT-01`에서 허용 지연·서버 부하·운영 복잡도를 기준으로 SSE, WebSocket, 주기적 조회 중 상태 전달 방식을 결정한다.
7. 통계·Weekly·클립·인기글·관심 알림·활동·차단과 `QA-04`를 진행한다.
8. 외부 승인을 확보하면 RSO·Tournament API·LCG CUP을 구현한다. 계정 소유 확인에 RSO가 필요하면 `EXT-01`을 앞당긴다.

## 단계별 완료 조건

| 단계 | 사용자가 할 수 있어야 하는 일 | 출시를 막는 조건 |
| --- | --- | --- |
| MVP | 학생 로그인, 프로필 공개 설정, Riot 등록, 전적 검색, Solo/Flex 학교 랭킹, 글·댓글·추천 | 소유 미확인 계정을 검증된 교내 계정으로 표시함, OAuth 미검증, 타인 데이터 수정 가능, 운영 키 조건 미충족 |
| Phase 2 | 듀오/5인 모집·참가, 10명 내전·팀 구성·수동 결과, 상태·알림 수신 | 초과 참가·중복 포지션, 미참가자 전용 정보 노출, 재접속 후 상태 불일치 |
| Phase 3 | 표본·기간이 명확한 통계, Weekly, 클립·인기글·활동·관심 알림 | 중복 집계, 동의 철회·차단·공개 설정 미반영 |
| Phase 4~5 | 검증된 Riot 연동, 승인된 범위의 대회 운영·기록 | RSO/Tournament 접근 승인 미확보, 결과 검증·수정 절차 부재 |

## 먼저 알아둘 제약

- Riot ID 조회는 계정 존재 확인이다. 계정 소유 인증 및 학교 소속 인증은 별개다. `D-02`에 MVP 랭킹의 현실적인 선택지를 적었다.
- DataGSM은 학생 외 유형도 반환할 수 있으므로 LCG 가입 자격을 별도로 검사한다. 구현 명세는 [인증 문서](02-auth-users.md)에 정리했다.
- Production 키 검토를 기획서 Phase 4까지 미루지 않는다. 공개 서비스에 필요한 키 조건은 MVP 출시 전에 확인해야 한다. [Riot API 키 문서](https://developer.riotgames.com/docs/portal#web-apis-api-keys)
- 내전 결과 자동 수집과 공개 가능 여부는 별도 확인 대상이다. 초기에는 수동 결과를 제안하며 참가자 공개 동의를 적용한다. [Riot LoL 정책](https://developer.riotgames.com/docs/lol#game-policy)
