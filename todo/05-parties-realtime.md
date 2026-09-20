# 파티 찾기·SignalR

## 모델과 규칙 제안

듀오와 5인 모집을 하나의 Party 모델로 관리한다. 듀오는 방장 포함 2명, 나머지는 최대 5명이다. Party에는 모집 모드, 정원, 포지션 모집 여부, 시작 시각, 보이스 조건, 티어 범위, 참가 방식, 방장, 상태, 버전을 둔다. 자유랭크·5인랭크 관계와 증바람 활성 여부는 `D-06`에서 확정한다.

```text
Party: Recruiting ↔ Full → InProgress → Closed
       Recruiting/Full → Cancelled 또는 Expired
Application: Pending → Approved / Rejected / Withdrawn
Member: Active → Left / Kicked
```

승인·입장 시 정원을 채우면 Full, 시작 전 이탈로 빈자리가 생기면 Recruiting으로 돌아간다. Pending 신청자는 정원에 넣지 않는다. 모집 종료와 실제 게임 종료는 구분하며 자동 게임 실행은 범위에 없다.

## TODO

- [ ] **PTY-01 | P2 | 파티 생성·설정·상태 전이** — 선행: `AUTH-04`, `USER-02`, `FND-03~05`, `D-06~07` 결정.
  - 구현: 제목·모드·정원·포지션 방식·티어·보이스(필수/선택/미사용)·즉시/예약 시작, 수정·모집 마감·취소·만료를 구현한다. 생성 시 방장을 참가자로 함께 저장한다.
  - 완료 기준: 일반·칼바람·증바람·자유랭크·5인 모집의 설정이 정책과 맞는다. 진행 중 정원 축소 등 불가능한 변경과 무권한 상태 변경이 거절된다.

- [ ] **PTY-02 | P2 | 신청·승인·거절·취소** — 선행: `PTY-01`.
  - 구현: 1·2순위 희망 포지션, 신청 메시지 필요 여부, 승인제 또는 즉시 참가 정책을 적용한다. DB 트랜잭션에서 중복 신청·확정 참가·정원·슬롯을 함께 검사한다.
  - 완료 기준: 마지막 한 자리에 동시에 요청해도 한 명만 확정된다. 포지션이 없는 모드는 포지션을 강제하지 않는다. 중복 요청은 기존 상태를 반환하거나 일관된 409를 반환한다.

- [ ] **PTY-03 | P2 | 포지션 배정·탈퇴·강퇴·방장 위임** — 선행: `PTY-02`.
  - 구현: TOP/JUNGLE/MID/ADC/SUPPORT 슬롯, 희망 포지션과 확정 포지션의 분리, 방장 슬롯 조정, 탈퇴·강퇴·위임·재신청 규칙을 구현한다.
  - 완료 기준: 포지션 슬롯이 중복 점유되지 않는다. 방장 없는 활성 파티가 생기지 않고 강퇴된 사용자의 참가자 전용 접근이 즉시 해제된다. 다른 파티와의 중복 확정 정책도 원자적으로 적용한다.

- [ ] **PTY-04 | P2 | 모집 목록·상세·검색** — 선행: `PTY-01~03`, `RIOT-03`, `USER-02`.
  - 구현: 모드·티어·포지션·보이스·시각·모집 상태 필터, 안정적 페이지네이션, 내 신청/참가 목록을 제공한다. Discord 등 연락 수단이 필요하면 확정 참가자에게만 공개한다.
  - 완료 기준: 정원·빈 슬롯·필터 결과가 실제 DB 상태와 일치한다. 비공개 티어/학교 정보가 필터나 작성자 정보로 노출되지 않는다.

- [ ] **PTY-05 | P2 | 모집 조건·만료 작업** — 선행: `PTY-04`, `FND-06`.
  - 구현: 예약 시작과 자동 만료, 방장 재개 가능 범위, 낡은 티어로 참가 조건을 판단할 때의 정책, 실제 Riot 큐 제한과 LCG 희망 조건의 차이를 처리한다.
  - 완료 기준: 종료된 파티에 늦은 승인 요청을 보내도 참가되지 않는다. 잘못된 게임 모드를 별도 공식 큐로 취급하지 않으며 시즌별 듀오 제한 등을 영구 하드코딩하지 않는다.

- [ ] **RT-01 | P2 | 인증된 SignalR 허브·그룹** — 선행: `AUTH-04`, `PTY-04`.
  - 구현: 로비 요약/파티 상세/사용자 알림 그룹을 나누고 구독 때 서버가 접근 권한을 검사한다. 연결 제한·메시지 크기·빈도 제한·로그의 토큰 제거를 적용한다.
  - 완료 기준: 사용자가 다른 사람의 userId나 임의 partyId로 비공개 그룹에 들어갈 수 없다. 강퇴·로그아웃·정지 이후 전용 이벤트가 전달되지 않는다. 연결 종료만으로 파티 참가를 취소하지 않는다.

- [ ] **RT-02 | P2 | 커밋 이후 이벤트·재접속 복구** — 선행: `RT-01`, `FND-06`, `PTY-02~03`.
  - 구현: PartyCreated, MemberJoined, MemberLeft, MemberKicked, PartyFull, PartyClosed와 필요한 상태 변경 이벤트를 커밋 후 발행한다. eventId·partyId·version·occurredAt을 포함하고 공개 요약과 참가자 payload를 분리한다.
  - 완료 기준: 재연결은 최신 REST 스냅샷으로 복구하고 누락·중복·역순 이벤트는 버전으로 처리한다. 전송 실패로 DB 참가를 롤백하지 않는다. 다중 서버 배포 시 backplane/공유 전송 구성을 검증한다.

## LCG API 초안

- `GET/POST /api/v1/parties`, `GET/PATCH /api/v1/parties/{partyId}`
- `POST /api/v1/parties/{partyId}/applications`, `DELETE /api/v1/parties/{partyId}/applications/me`
- `POST /api/v1/parties/{partyId}/applications/{applicationId}/approve` 또는 `/reject`
- `DELETE /api/v1/parties/{partyId}/members/me`, `DELETE /api/v1/parties/{partyId}/members/{userId}`
- `PUT /api/v1/parties/{partyId}/slots/{position}`, `POST /api/v1/parties/{partyId}/transfer-host`
- `POST /api/v1/parties/{partyId}/start`, `/close`, `/cancel`
- SignalR: `/hubs/parties`, 사용자 알림은 `/hubs/notifications`

상태 변경은 초기에는 REST로 수행하고 SignalR은 변경 전달에 집중한다. 요청 actor는 인증 컨텍스트에서 읽으며 body의 userId로 행동 주체를 정하지 않는다.
