# frontend — CONTEXT

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 모듈 역할

- 사용자 화면을 제공하는 React SPA(`frontend/`)다. 관리자 화면(JSP)은 범위가 아니다([FE-D002](decisions.md#1-확정-결정)).
- FE-01(Issue #11)은 이후 화면이 공통으로 쓸 기반을 만들었다: API Client, 인증 상태, 오류 처리, 라우팅.
- FE-03(Issue #20)은 회원가입 화면을 만든다. 로그인·OTP·Google 로그인·회원 정보 화면은 이후 FE 작업이다.

## 2. 액터

| 액터 | 설명 |
| --- | --- |
| 브라우저 사용자 | SPA를 여는 사람. 인증 여부에 따라 공개 화면 또는 인증 필요 화면에 접근한다 |
| Backend API | `/api` 아래 Spring Boot API. 실제 인증·인가를 검증한다([FE-D012](decisions.md#1-확정-결정)) |

## 3. 유스케이스

| 유스케이스 | 요지 |
| --- | --- |
| 앱 시작 시 인증 초기화 | 인증 어댑터로 현재 인증 여부를 확인해 인증 상태를 정한다. 실패 유형에 따라 미인증 또는 초기화 오류가 된다([FE-D009](decisions.md#1-확정-결정)) |
| 인증 필요 화면 접근 | Auth Guard가 인증 상태에 따라 로딩·오류·로그인 이동·화면 표시 중 하나를 고른다([FE-D012](decisions.md#1-확정-결정)) |
| API 호출과 오류 표시 | 공통 Client가 요청을 검증·전송하고 오류를 정규화한다. 401은 지정한 인증 영향 범위에 따라 인증 상태에 반영한다([FE-D007](decisions.md#1-확정-결정), [FE-D010](decisions.md#1-확정-결정)) |
| 회원가입 | 브라우저 사용자가 username·이메일·닉네임·비밀번호를 입력해 가입한다. 가입한 계정은 첫 로그인 OTP 전까지 PENDING이다(계정 상태는 member [CONTEXT](../member/CONTEXT.md#4-모듈-용어)). 완료 안내 화면으로 이동한다([FE-D022](decisions.md#1-확정-결정)~[FE-D026](decisions.md#1-확정-결정)) |

## 4. 모듈 용어

| 용어 | 뜻 |
| --- | --- |
| 인증 상태 | 앱 전역의 유일한 전역 값. `initializing`·`unauthenticated`·`authenticated` 중 하나 |
| initError | `initializing` 상태에서 초기화가 미인증이 아닌 이유(503·5xx·network·timeout 등)로 실패한 것 |
| 인증 영향 범위 | 요청마다 지정하는 401의 의미: `none`(인증과 무관), `initialization`(초기화 요청), `session`(현재 인증에 의존) |
| 인증 세대 | 인증 주체가 바뀔 때마다 증가하는 번호. 오래된 응답이 새 상태를 덮어쓰는 것을 막는다 |
| 인증 어댑터 | 인증 초기화·판정을 Backend 계약과 분리하는 인터페이스 |
| 미연결 어댑터 | Backend 인증 계약 전에 운영·일반 개발에서 쓰는 어댑터. 네트워크 호출 없이 항상 미인증 |
| 가안 Mock | 테스트에서만 쓰는 어댑터·응답. 확정 계약이 아님을 "가안"으로 표시한다 |
| Auth Guard | 인증 필요 화면 앞의 화면 접근 제어. 보안 경계가 아니다 |
| CSRF 토큰 확보 | 상태 변경 요청 전에 `XSRF-TOKEN` 쿠키를 읽고, 없으면 `/api/v1/auth/csrf`를 한 번만(single-flight) 호출해 쿠키를 받는 것 |
