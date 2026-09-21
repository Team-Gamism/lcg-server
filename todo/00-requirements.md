# 요구사항 분석 및 결정 사항

## 기획서 → 서버 작업 대응

| 원문 절 | 요구사항 / 서버 해석 | 우선순위·작업 |
| --- | --- | --- |
| 1~2, 25 | 교내 플레이어 발견과 커뮤니티·게임 모집 연결 | 전 단계; Users의 식별자와 공개 DTO를 공통으로 사용 |
| 3.1 | 학교 계정으로 가입·로그인, 중복 회원 방지 | P0 `AUTH-01~05` |
| 3.2 | Riot ID 등록, PUUID 식별, 이후 RSO | P1 `RIOT-03`, P4 `EXT-01`; `D-02` 선결 |
| 4 | 닉네임·학년·포지션·소개·랭크·전적·활동·공개 설정 | P1 `USER-01~03`, `MAT-01~03`; P3 `SOC-01` |
| 5.1~5.3 | Riot ID 검색, 플레이어 요약, 경기 목록·상세 | P1 `MAT-01~02` |
| 5.4 | 챔피언 숙련도와 모스트 표시 | P1 `MAT-03` |
| 6 | Solo/Flex 랭킹, 학년·포지션 필터, 공식 랭크만 사용 | P1 `RANK-01~02` |
| 7.1~7.2 | 학교 챔피언·패치 통계, 많이 플레이한 사용자, 최소 표본 | P3 `STAT-01~03` |
| 7.3 | 주간 게임·챔피언·파티·내전 요약 | P3 `STAT-04` |
| 8~9 | 듀오 모집, 티어·포지션·보이스 필터, 참가 신청 | P2 `PTY-01~04` |
| 10 | 최대 5명, 모드·시작 시각·포지션 유무·희망 포지션 | P2 `PTY-01~05` |
| 11 | 생성·입장·탈퇴·강퇴·정원·종료 실시간 반영 | P2 `RT-01~02` |
| 12.1~12.3 | 내전 10명, 포지션별 2명, Blue/Red 수동·랜덤 편성 | P2 `INH-01~03` |
| 12.4 | 내전 결과, 데이터 취득 가능성·참가자 동의 | P2 `INH-04~05`; P4 `EXT-02` |
| 13 | 카테고리, 글 CRUD, 댓글·대댓글·추천·조회·신고·인기글 | P1 `COM-01~04`; P3 `COM-05` |
| 13.2 | 작성자 Riot 배지 및 공개 여부 | P1 `USER-02`, `COM-01` |
| 14 | 외부 영상 링크 방식 클립 게시판 | P3 `COM-06` |
| 15 | 댓글·파티 충원·내전 알림, 읽음·실시간 전달 | P2 `NTF-01~02` |
| 16 | 관심 모드 저장, 신규 파티 알림 | P3 `NTF-03` |
| 17~18 | 홈 요약과 전체 사용자 흐름 | P1 `HOME-01`; P2/P3 `HOME-02`, 단계별 QA |
| 19 | Modular Monolith, Kotlin + Spring Boot, PostgreSQL, Redis로 기술 선택 변경 반영; 실시간 통신 방식은 P2 결정 | P0 `FND-02~05`; P2 `FND-06`, `RT-01` |
| 20~22 | Riot 호출 계층, 캐시·제한·실패 처리·정적 데이터 | P0 `RIOT-01~02`; P1 `RIOT-04~05` |
| 23 | Account/League/Party/Inhouse/Community/Social | 위 모듈 + P3 `SOC-01~02`; 차단의 상세 동작은 보완 제안 |
| 24 | 5단계 개발 로드맵, LCG CUP | [전체 순서](README.md), P4 `EXT-01~04` |

기획에 직접 명시되지 않은 운영자 처리, 탈퇴, 작업 재시도, 감사 기록, CI·백업은 실제 운영을 위한 **보완 제안**이다. 영상 직접 업로드, 채팅/DM, 결제, 자체 MMR, 자동 실력 밸런싱은 현재 구현 범위에 넣지 않는다.

## 채택한 기술 방향

2026-09-21 대화에서 개발·유지보수 편의를 우선해 다음 방향을 선택했다.

- 서버는 **Kotlin + Spring Boot**로 구현한다. [Spring Boot의 Kotlin 지원](https://docs.spring.io/spring-boot/reference/features/kotlin.html)을 기준으로 프로젝트를 구성한다.
- PostgreSQL, Redis, Modular Monolith 구조를 사용한다. DB·캐시·모듈별 저장 책임은 아래 원칙을 따른다.
- 학교 OAuth는 [DataGSM 공식 Java/Kotlin SDK](https://github.com/themoment-team/datagsm-oauth-sdk-java)를 사용한다. SDK의 버전·PKCE·토큰 교환·사용자 응답 호환성을 `AUTH-01`에서 확인하고, LCG 회원·세션·권한 정책은 애플리케이션에서 구현한다.
- MVP는 HTTP API 중심으로 구현한다. 파티·내전·알림의 상태 전달 방식은 P2의 `RT-01`에서 결정하며, WebSocket 도입을 미리 확정하지 않는다.

Gradle Kotlin DSL과 Spring Data JPA 등 구체적인 구성은 [서버 기반 문서](01-foundation.md)의 제안으로 두고 착수 시 확정한다. JDK·Kotlin·Spring Boot·SDK·DB·Redis 버전도 호환성과 지원 상태를 확인한 뒤 고정한다. 기술 방향 선택만으로 `FND-01` 전체나 아래 정책 결정이 완료된 것은 아니다.

## 구현 전에 결정할 사항

질문 때문에 계획 수립을 중단하지 않고 다음 기본안을 제안한다. `FND-01`에서 채택·변경과 이유를 기록하고 API·테스트에 동일하게 적용한다.

| ID | 결정할 내용 | 제안 기본안 및 영향 | 결정 시점 |
| --- | --- | --- | --- |
| D-01 | 재학생 외 교사·졸업생·자퇴생 허용 여부 | 첫 버전은 ACTIVE 재학생만. 학교 응답의 역할을 LCG 운영자 권한으로 승격하지 않음 | OAuth 회원 생성 전 |
| D-02 | RSO 이전 소유 확인과 교내 랭킹 대상 | ID 입력은 `Unverified`. 검증된 학교 랭킹에는 소유 확인된 계정만 포함. MVP 전에 RSO를 앞당길지, 별도 소유 확인 절차를 정의할지 결정. 둘 다 어렵다면 자기 신고 목록을 명시적으로 구분하고 **검증된 랭킹 출시는 보류** | Riot 등록·랭킹 설계 전 |
| D-03 | 계정 수·중복 연결 | 사용자당 대표 Riot 계정 1개. 검증된 PUUID는 한 사용자만 소유. 미인증 등록으로 타인의 RSO 연동을 선점하지 못하도록 별도 제약 | Riot DB 설계 전 |
| D-04 | 로그인 세션 방식·웹 배포 도메인 | 같은 사이트의 웹/API + 서버 세션, HttpOnly/Secure 쿠키, 변경 요청 CSRF 방어. 다른 사이트 배포라면 쿠키/CORS 정책 재설계 | Auth 및 프론트 계약 전 |
| D-05 | 조회 공개 범위 | MVP는 학교 회원에게 서비스 데이터 제공. 학년·Riot 연결·티어·전적·활동·랭킹/통계 참여를 개별 제어. 학교 소속 매핑 공개와 일반 Riot 전적 검색을 구분 | 프로필/API 설계 전 |
| D-06 | `자유랭크`와 `5인랭크`, `증바람`의 의미 | 내부 모집 모드와 Riot queueId를 분리. 5인랭크가 자유랭크의 5인 모집 옵션인지 확인하기 전 별도 공식 큐로 단정하지 않음. 증바람 등 제공 여부는 모드 설정으로 관리 | 파티 모드 확정 전 |
| D-07 | 즉시 참가/승인제, 중복 참가, 방장 이탈 | 기본 승인제 제안(기획에 신청·승인 알림 존재). 대기자는 정원 제외. 진행 중 모집에는 한 개만 확정 참가하도록 제안. 방장은 위임 또는 취소 후 이탈 | 파티·내전 설계 전 |
| D-08 | 랭킹 동률, 포지션 필터 | 티어→디비전→LP, 동률 공동 순위. 주 포지션 기준 필터, 보조 정렬은 표시 안정성만 보장. 미배치·정보 없음 별도 표시 | 랭킹 구현 전 |
| D-09 | 통계 집계 범위·동의·보존 | 동의한 검증 계정의 수집된 경기만, 큐·기간·패치 분리. 30게임 최소 표본은 초기 설정값. 탈퇴·동의 철회 시 사용자 연결 제거와 재집계 방침 확정 | 수집 DB 설계 및 통계 전 |
| D-10 | 내전 결과 공개·수정 권한 | 방장 수동 입력, 참가자 확인, 수정 이력. 참가자별 공개 동의가 충족된 범위만 반환 | 결과 구현 전 |
| D-11 | 초기 Riot 지역·외부 승인 | KR 우선 제안. Riot 제품 등록, 실제 공개 범위와 키 자격, RSO 승인 준비를 MVP에서 확인 | 외부 연동 시작 시 |

`D-02`는 기획의 현실적인 의존성이다. PUUID를 찾았다는 이유만으로 소유 확인을 완료 처리하지 않는다. 소유 확인 없는 MVP에서는 전적 검색·커뮤니티를 개발할 수 있지만, 검증된 학교 랭킹과 학교 통계의 출시 조건을 충족했다고 표시하면 안 된다.

## 도메인과 저장 책임

| 모듈 | 영구 데이터 후보 | 핵심 제약 |
| --- | --- | --- |
| Auth/Users | User, SchoolIdentity, UserProfile, PrivacySettings, Consent | `(provider, providerUserId)` 고유; 공개 프로필과 학교 원본 정보 분리 |
| Riot/Matches | RiotAccount, RiotAccountLink, RankSnapshot, Match, MatchParticipant, SyncJob | PUUID 식별, 검증 상태; matchId 및 `(matchId, puuid)` 고유 |
| Rankings | RankingSnapshot 또는 DB 조회 모델 | 큐·시즌·필터·스냅샷 시각, 공개 동의 반영 |
| Parties | Party, PartyApplication, PartyMember, PartySlot | 인원·슬롯·참가 상태 변경을 한 트랜잭션에서 검사 |
| Inhouses | Inhouse, InhouseMember, InhouseTeamAssignment, InhouseResult, ResultConsent | 10명, 팀당 5명·포지션 1명, 결과 버전 |
| Community | Post, Comment, Reaction, Report, ModerationAction | 소유권, 대댓글 깊이, 중복 추천, 운영자 감사 |
| Notifications | Notification, NotificationPreference, OutboxMessage, ScheduledJob | 수신자 권한, 이벤트 중복 방지, 취소된 일정 발송 방지 |
| Statistics/Social | StatisticsAggregate, WeeklySummary, Activity, UserBlock | 집계 버전·기간·표본·동의, 차단 방향·범위 |
| Tournament(후속) | Tournament, Team, Roster, Registration, BracketMatch | 명단 잠금·대진 버전·중복 결과 방지 |

PostgreSQL을 회원·참가·결과의 원장으로 사용한다. Redis는 캐시·세션·일시적인 제한/동기화 용도다. Redis 파티 캐시 소실이나 상태 전달 연결 종료가 참가자 탈퇴로 이어져서는 안 된다.

## 공통 데이터·권한 원칙

- DB 시간은 UTC, API는 오프셋이 명확한 ISO 8601, 학교 일정/주간 집계는 `Asia/Seoul` 기준으로 계산한다.
- 인증 없음/인증됨/정지됨/탈퇴함과 작성자/방장/참가자/운영자의 권한을 분리한다. 로그인 여부만으로 수정 권한을 주지 않는다.
- 프로필·홈·검색·작성자 배지·랭킹·알림·상태 전달 채널 모두 동일한 공개 범위를 적용한다. 설정 변경 시 기존 캐시도 무효화한다.
- 모든 목록에 페이지 크기 상한과 안정적인 정렬을 둔다. 공개 ID와 외부 식별자를 구분한다.
- 재시도 가능한 작업은 중복 실행에도 안전하게 만든다. 참가·추천·결과·알림은 DB 제약과 트랜잭션으로 보장한다.
