# frontend — decisions

frontend 결정 이력·이유·미결의 원본이다. 현재 유효한 업무 규칙은 [policy.md](../policy.md)에 둔다.
작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 확정 결정

출처의 "FE n차 Qm"은 FE-01(Issue #11) 정책 결정 라운드다. 모두 2026-11-26 결정.

| ID | 결정 | 이유 | 출처·날짜 |
| --- | --- | --- | --- |
| FE-D001 | 작업 환경: FE 작업은 저장소 밖 형제 경로의 Git Worktree에서 하고, 그 경로에서 새 Agent 세션을 연다. Git에 올리지 않는 개인 Agent 하네스(`.claude/`, `CLAUDE.local.md`)는 사용자가 직접 Worktree로 복사한다. | Worktree는 추적 파일만 꺼내므로 하네스가 따라오지 않는다. 복사하면 Hook·권한·Skill이 그대로 동작하고, 링크 방식의 삭제 전파 위험이 없다. | FE 1차 Q1, 2026-11-26 |
| FE-D002 | 실행 구조: `frontend/`는 독립 React SPA다. 브라우저 코드는 API를 상대 경로 `/api`로 호출하고, 개발 중에는 Vite dev server proxy(`/api` → Backend, 기본 `http://localhost:8080`)로 같은 출처를 만든다. proxy 대상은 `VITE_`로 시작하지 않는 환경변수로 받는다. 개발 서버는 5173 포트를 `strictPort`로 쓴다. JSP는 관리자 화면 전용으로 분리한다. 운영 제공 방식은 [FE-U01](#2-미결). | 같은 출처면 CORS가 필요 없고 쿠키·CSRF 결정이 단순해지며 Backend 변경이 없다. `VITE_` 변수는 번들에 포함되므로 서버 측 설정을 넣지 않는다. | FE 1차 Q2, FE 2차 Q9, 2026-11-26 |
| FE-D003 | 언어·도구: TypeScript + Vite + npm + Vitest + React Testing Library + MSW. MSW는 테스트(`msw/node`)에서만 쓴다. 정적 검사는 Vite react-ts 템플릿의 ESLint와 `tsc --noEmit`이고 Prettier는 도입하지 않는다. Node 24를 `engines`·`.nvmrc`로 고정한다. E2E는 [FE-U07](#2-미결). | Backend 응답 계약(ProblemDetail `code`, `errors[]`)을 타입으로 옮겨 실수를 일찍 잡는다. Mock을 브라우저 개발 경로에 두지 않는다. | FE 1차 Q3, FE 2차 Q10·Q11, 2026-11-26 |
| FE-D004 | 문서·ID: FE 문서는 `docs/frontend/` 모듈 문서 7종이고 접두어 `FE`(`FE-D###`·`FE-U##`·`FE-T###`·`FE-W##`)를 쓴다. 모듈 규칙은 [frontend/AGENTS.md](../../frontend/AGENTS.md)다. FE 작업에서 member 문서는 고치지 않고, member 작업과의 연결은 [PROGRESS.md](PROGRESS.md)에만 적는다. | 다른 모듈과 같은 Workflow로 관리한다. 병렬로 진행 중인 member 작업 브랜치와 문서 충돌을 피한다. | FE 1차 Q4, FE 4차 Q6, 2026-11-26 |
| FE-D005 | 인증 어댑터: Backend 계약이 확정되지 않은 인증 초기화·판정은 어댑터 인터페이스 뒤에 둔다. 운영·일반 개발 환경은 미연결 어댑터를 쓴다(네트워크 호출 없음, 항상 unauthenticated, 인증 성공 경로 없음). 가안 Mock 어댑터는 테스트 전용이며 "가안"으로 표시한다. 운영 코드가 `src/test`를 직접·간접으로 import하지 못하게 막고, 환경변수로 Mock을 켜는 경로를 두지 않으며, 빌드 결과를 검사한다. 실제 어댑터 교체는 [FE-U02](#2-미결). | 계약 전에도 병렬로 진행하고, 계약이 정해지면 어댑터만 바꾼다. Backend에 로그인 수단이 없는 동안 "항상 미인증"이 사실과 같고, 운영에서 임의로 인증되는 길이 구조적으로 없다. | FE 1차 Q5, FE 4차 Q2, 2026-11-26 |
| FE-D006 | 기준 브랜치: FE-01은 `origin/main`(81e78c8)에서 분기하고, 근거로 삼는 Backend 결정은 main에 있는 [G-D003~G-D005](../policy.md#3-공통-결정-이력)뿐이다. 다른 작업 브랜치의 결정을 main에 반영하는 일은 별도 승인을 받는다. | 병렬 작업 중인 member 브랜치의 결정은 main에 아직 없다. FE-01은 회원가입 화면을 범위에 두지 않으므로 main의 결정만으로 진행할 수 있다. | FE 1차 Q6, 2026-11-26 |
| FE-D007 | HTTP Client: Axios를 쓴다. 기본 Timeout은 10초이고 요청별로 재정의할 수 있다. 잘못된 값으로 Timeout이 꺼지지 않도록 값을 검증한다. 자동 Retry, Token Refresh, 401 자동 재요청은 하지 않는다. 오류는 정규화한다. HTTP 오류는 status를 항상 보존하고, ProblemDetail일 때만 `code`를 가진다. Backend에 없는 `code`를 만들지 않는다. 2xx 형식 오류, network, timeout, canceled를 구분한다. HTTP Timeout과 인증 초기화 전체의 종료 보장은 별개의 장치다. | 사용자 선택(추천은 fetch 래퍼). 자동 Retry는 멱등하지 않은 요청을 중복 실행할 수 있고 인증 세대 규칙을 복잡하게 만든다. | FE 2차 Q3, FE 4차 Q1·보완, 2026-11-26 |
| FE-D008 | 의존성: 정확한 버전으로 고정하고 lockfile을 커밋하며, 재현 설치는 `npm ci`로 한다. 공개 후 14일이 지난 버전만 고르고, 예외는 사용자 승인을 받는다. 최초 추가와 `npm ci`를 구분한다. 전이 의존성도 검토한다. 공개일·보안 권고·선정 버전·근거를 기록한다. `npm audit fix`(`--force` 포함), `--force`, `--legacy-peer-deps`는 승인 없이 쓰지 않는다. audit 실패나 취약점을 숨기거나 PASS로 처리하지 않는다. 후보: React Router 7.18.5, TypeScript 6.0.3. 설치 직전에 14일·권고·engines·peer·상호 호환·Axios 동작·전이 의존성·audit을 다시 확인하고, 버전 변경이나 예외는 보고한다. | 재현 가능한 설치와 공급망 위험 관리. React Router v7은 data 모드 API를 확인했고, TypeScript 7은 typescript-eslint 8의 peer 범위 밖이다. | FE 3차 Q2·보완, FE 4차 Q4·Q5, 2026-11-26 |
| FE-D009 | 인증 상태: Context + useReducer로 두고, 전역 값은 인증 상태 하나로 한정한다. 상태는 Discriminated Union `initializing`·`unauthenticated`·`authenticated` 3가지이고, 초기화 오류는 `initializing` 안의 `initError`로 표현한다. 초기화에서 401은 미인증이고, 503·5xx·network·timeout은 미인증이 아니다(initError). 무한 로딩이나 로그인 Redirect로 빠지지 않는다. 재시도하면 initError를 정리하고 최신 결과만 반영한다. Reducer는 순수 함수다. OTP 대기 상태는 확장 지점만 두며 이름은 `PENDING`과 다르게 한다([FE-U08](#2-미결)). 확정되지 않은 API 응답과 회원 정보 타입은 임의로 확정하지 않는다. | 인증 상태 외의 서버 상태는 전역에 두지 않는다. 장애를 미인증으로 오판해 사용자를 로그아웃시키지 않는다. | FE 2차 Q4·Q7·보완, FE 3차 Q3·보완, FE 4차 보완, 2026-11-26 |
| FE-D010 | 오류·401 처리: 403은 권한 없음 화면, 503 `AUTH_SESSION_STORE_UNAVAILABLE`은 로그아웃 없이 재시도 안내, 그 밖의 5xx·network는 공통 오류로 처리한다. 401은 요청마다 인증 영향 범위(none·initialization·session)를 타입으로 반드시 지정하게 하고 기본값은 두지 않는다. 판정에는 범위·`code`·현재 상태·인증 세대를 쓰고, 알 수 없는 `code`와 중복 변경을 막는다. Interceptor는 직접 Redirect하지 않는다. 401이면 상태만 갱신하고 이동은 가드가 맡는다. 만료 구분·Refresh·자동 재요청은 Backend 계약 뒤([FE-U02](#2-미결)). | 모든 401을 같게 처리하면 공개 요청의 401이 로그아웃을 일으킨다. 근거: [G-D003](../policy.md#3-공통-결정-이력), [G-D007](../policy.md#3-공통-결정-이력). | FE 2차 Q8·보완, FE 3차 Q4·보완, 2026-11-26 |
| FE-D011 | 동시성: 인증 세대 번호를 둔다. 새 인증 성공·로그아웃·세션 상실·인증 주체 변경 때만 증가하고, initError·메시지·회원 정보 갱신·중복 이벤트로는 증가하지 않는다. 초기화 재시도는 요청 ID로 순서를 관리한다. 초기화 응답은 요청 ID, `initializing` 상태, 시작 때 세대가 모두 맞을 때만 반영한다. 요청을 일괄 abort하지 않는다. 이전 인증에 의존하는 요청만 취소하고, 공개 요청과 새 인증 요청은 취소하지 않는다. 취소는 사용자 오류로 표시하지 않는다. 호출자 signal과 결합할 때 충돌하지 않게 한다. abort해도 서버 실행이 롤백된다고 가정하지 않으며, abort와 별개로 오래된 세대의 응답은 무시한다. | 동시 401이 로그아웃·이동·메시지를 중복으로 만들거나, 오래된 응답이 새 인증 상태를 덮어쓰는 것을 막는다. | FE 2차 Q8 보완, FE 3차 Q5·보완, FE 4차 보완, 2026-11-26 |
| FE-D012 | 라우팅: React Router v7 data 모드를 쓰고, 공개 레이아웃·인증 필요 레이아웃 경로와 `errorElement`를 둔다. Auth Guard는 화면 접근 제어일 뿐 보안 경계가 아니다. 실제 인증·인가는 Backend Spring Security가 검증한다. `errorElement`는 라우팅(loader·렌더) 오류만 처리하고, 비동기 API 오류는 API Client와 호출부가 처리한다. 로그인 뒤 돌아갈 `from`은 안전한 내부 경로만 허용하고 그 밖은 `/`로 보낸다(후속 [FE-U03](#2-미결)). | 프론트 검사는 우회할 수 있으므로 보안 판단은 서버에 둔다. 오류 처리 책임을 나눠 중복 처리를 막는다. | FE 2차 Q6·보완, FE 4차 보완, 2026-11-26 |
| FE-D013 | 요청 URL 검증: 공통 Client는 최종 경로가 같은 출처의 `/api/` 아래인지 검증한다(`..`, 퍼센트 인코딩, 역슬래시, 절대·`//` URL 거부). 요청별로 baseURL과 보안 관련 옵션을 바꾸지 못하게 하고, 원본 Axios 인스턴스는 밖에 노출하지 않는다. | 인증 쿠키가 다른 출처나 의도하지 않은 경로로 나가는 것을 막는다. | FE 4차 보완, 2026-11-26 |
| FE-D014 | XSRF: [G-U02](../policy.md#4-공통-미결) 확정 전까지 Axios의 XSRF 자동 처리를 명시적으로 끈다. 선정 버전의 공식 문서와 실제 동작을 기준으로 삼고, XSRF 쿠키가 있어도 헤더가 붙지 않는지 검증한다. 검증은 jsdom 자동 테스트와 실제 브라우저 수동 확인으로 하고 결과를 구분해 기록한다. 수동 확인을 하지 않았으면 PASS로 기록하지 않으며, 영구 검증 페이지나 Mock 경로를 추가하지 않는다. Backend CSRF 설정은 바꾸지 않는다. 확정 뒤 바꿀 수 있게 둔다([FE-U04](#2-미결)). | CSRF 방식이 미결([G-D005](../policy.md#3-공통-결정-이력))인 동안 정해지지 않은 헤더를 보내지 않는다. Node 테스트만으로는 브라우저 동작을 보장할 수 없다. | FE 3차 Q1·보완, FE 4차 Q3·보완, 2026-11-26 |
| FE-D015 | lint 도구 체인의 `minimatch` 권고([GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249), moderate, `>=7.0.0 <11.0.1`)는 FE-01에서 위험을 수용한다. `eslint` 10.14.0·`typescript-eslint` 8.72.2를 유지하고, audit 결과는 미해결로 [regression-log.md](regression-log.md)에 그대로 기록한다. 수정 버전(`eslint` 10.15.0, `typescript-eslint` 8.74.0)이 공개 14일을 넘긴 뒤(2026-12-07 이후) 별도 작업에서 올린다. 14일 예외와 `overrides` 강제는 쓰지 않는다. | 영향 패키지는 개발 전용 lint 체인이고 운영 번들에 포함되지 않으며, glob 패턴은 저장소 설정이 정한다. 수정 버전은 공개 14일 미만이라 [FE-D008](#1-확정-결정) 예외가 필요하다. | FE-01 구현 중 의존성 설치 전 재검증 질문 Q1, 2026-11-26 |
| FE-D016 | 인증 초기화가 결과(인증·미인증)로 끝난 뒤에는 adapter 등이 바뀌어도 다시 초기화하지 않는다. 초기화가 끝나기 전의 adapter 교체는 새 adapter로 다시 시작하고, 오류(initError) 뒤 재시도는 허용한다. 로그아웃·재로그인으로 상태를 다시 여는 방법은 BE-03 계약 뒤 [FE-U02](#2-미결)와 함께 다시 검토한다. | 초기화가 끝난 뒤 다시 초기화하면 Reducer는 결과를 버리지만 세대 관리(scope)는 세대를 올려 둘이 어긋나고, 세션 상실 401이 반영되지 않는다([FE-D011](#1-확정-결정)). 운영은 미연결 어댑터로 고정이라 교체가 필요 없다. | FE-01 CodeRabbit 리뷰 반영 질문 Q1, 2026-11-26 |

## 2. 미결

| ID | 미결 | 상태 | 출처·결정 시점 |
| --- | --- | --- | --- |
| FE-U01 | 운영 환경에서 React 정적 파일을 누가 어떻게 제공할지(예: Caddy, Spring) | 미결 | FE 1차 Q2. 결정 시점: 운영 배포 구성 작업 |
| FE-U02 | 실제 Auth Adapter 교체: 인증 쿠키, 현재 회원 조회 API, 401 만료 구분, Refresh, Google OIDC 콜백 계약에 맞춘 구현 | 미결 | FE 4차 Q2. 결정 주체: Backend 계약(BE-03~05) 확정 뒤 |
| FE-U03 | 로그인 성공 뒤 이동 처리와 Open Redirect 방어 후속 | 미결 | FE 4차 보완. 결정 시점: 로그인 화면 FE 작업 |
| FE-U04 | XSRF 자동 처리 활성화 방식 | 미결 | FE 3차 Q1. 결정 시점: [G-U02](../policy.md#4-공통-미결) 확정 뒤 |
| FE-U05 | React Router v8 이전 | 미결 | FE 4차 Q4. 결정 시점: 별도 작업 |
| FE-U06 | TanStack Query 도입 | 미결 (보류) | FE 2차 Q5. 결정 시점: 실제 조회 화면 FE 작업 |
| FE-U07 | E2E 테스트 도구와 Frontend CI | 미결 | FE 1차 Q3, FE 2차 Q2. 결정 시점: 화면이 생기는 FE 작업 |
| FE-U08 | OTP 대기 상태 표현 | 미결 | FE 2차 Q7. 결정 시점: Backend 계약(BE-03) 확정 뒤 |

## 3. 변경 이력

| 날짜 | 변경 |
| --- | --- |
| 2026-11-26 | FE-D001~FE-D014, FE-U01~FE-U08 기록(Issue #11, FE-01 정책 1~4차) |
| 2026-11-26 | FE-D015 기록(FE-01 구현 중 설치 전 재검증, lint 체인 audit 위험 수용) |
| 2026-11-26 | FE-D016 기록(FE-01 CodeRabbit 리뷰 반영, 초기화 완료 뒤 재초기화 안 함) |
