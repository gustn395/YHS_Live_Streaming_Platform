# frontend — module

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. To-Be 설계

확정된 설계 요소와 근거 결정이다. 구조는 FE-01 구현 계획(2026-11-26 승인)에서 정했다.

```text
frontend/
  src/main.tsx                 진입점. 미연결 어댑터만 조립한다
  src/app/                     라우터·Provider 조립, 앱 단일 API Client, 화면(pages/)
  src/features/auth/           인증 상태·Provider·가드·세대 관리·어댑터
  src/shared/api/              API Client, 오류 정규화, 요청 옵션·URL 검증
  src/test/                    테스트 전용(setup, MSW server, 가안 어댑터, lint 규칙 테스트)
  scripts/                     빌드 검사(모듈 그래프·dist 표지)
```

- 의존 방향은 `app → features → shared`만 허용한다. `axios`는 `src/shared/api`에서만, `src/test`·`msw`는 테스트에서만 import한다.
  ESLint `no-restricted-imports`(상대 경로·Alias 포함)로 막고, 운영 빌드 모듈 그래프 검사가 동적 import까지 막는다.
- `shared/api`는 `AuthBridge` 인터페이스만 알고, `features/auth`가 구현을 붙인다(순환 의존 없음).
- 저장소 루트 `.gitignore`가 `build/`를 제외하므로 빌드 검사 코드는 `frontend/scripts/`에 둔다.

| 구성 요소 | 설계 요지 | 근거 |
| --- | --- | --- |
| 실행 구조 | `frontend/` 독립 SPA, `/api` 상대 경로, 개발 중 Vite proxy | [FE-D002](decisions.md#1-확정-결정) |
| API Client | Axios 기반 공통 Client. 제한된 요청 옵션, `/api/` 경로 검증, Timeout, 오류 정규화, XSRF 자동 처리 비활성 | [FE-D007](decisions.md#1-확정-결정), [FE-D013](decisions.md#1-확정-결정), [FE-D014](decisions.md#1-확정-결정) |
| CSRF 토큰 | 공통 Client가 상태 변경 요청에 `X-XSRF-TOKEN`을 직접 부착, 쿠키가 없으면 `/api/v1/auth/csrf` single-flight 호출, CSRF 403 전용 오류 분류 | [FE-D020](decisions.md#1-확정-결정), [FE-D021](decisions.md#1-확정-결정) |
| 회원가입 화면 | 공개 경로의 가입 화면과 완료 안내 화면. 클라이언트 형식 검증, 서버 오류 필드 표시, 429·503 안내, 비밀번호 표시 토글. 파일·경로 구조는 2장 As-Is | [FE-D022](decisions.md#1-확정-결정)~[FE-D026](decisions.md#1-확정-결정), [FE-D028](decisions.md#1-확정-결정) |
| 스타일 기반 | CSS Modules + 전역 CSS 변수 토큰(`src/styles/tokens.css`), 시스템 라이트·다크 자동. 토큰 값은 아래 표 | [FE-D027](decisions.md#1-확정-결정), [FE-D031](decisions.md#1-확정-결정) |

| 토큰 | 라이트 | 다크 |
| --- | --- | --- |
| `--color-brand` / `--color-on-brand` | `#6D28D9` / `#FFFFFF` | `#A78BFA` / `#1A1033` |
| `--color-bg` / `--color-surface` | `#F7F7FA` / `#FFFFFF` | `#0F0F14` / `#1A1A22` |
| `--color-text` / `--color-text-muted` | `#1A1A22` / `#55556A` | `#EDEDF2` / `#A9A9BC` |
| `--color-border` | `#D4D4DE` | `#3A3A48` |
| `--color-danger` / `--color-danger-bg` | `#B42318` / `#FEF3F2` | `#F97066` / `#2A1414` |
| `--color-warning-bg` | `#FFFAEB` | `#2A2410` |
| `--color-focus` | `#6D28D9` | `#C4B5FD` |
| 간격·모서리·글꼴 | 4·8·12·16·24·32px, 8px, `system-ui, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif` | 같음 |
| 인증 어댑터 | 인터페이스 + 미연결 어댑터(운영·일반 개발) + 가안 Mock(테스트 전용) | [FE-D005](decisions.md#1-확정-결정) |
| 인증 상태 | Context + useReducer, Discriminated Union 3가지 상태 + initError, 순수 Reducer | [FE-D009](decisions.md#1-확정-결정) |
| 401·오류 처리 | 요청별 인증 영향 범위 필수 지정, 인증 세대·초기화 요청 ID, 선택적 취소 | [FE-D010](decisions.md#1-확정-결정), [FE-D011](decisions.md#1-확정-결정) |
| 라우팅 | React Router v7 data 모드, 공개·인증 필요 레이아웃, Auth Guard, 라우팅 오류 전용 `errorElement` | [FE-D012](decisions.md#1-확정-결정) |

## 2. As-Is

2026-11-27, 브랜치 `feature/20-frontend-signup` 작업 트리 기준(기준 커밋 d47736c, 커밋 전). FE-01(#14) 이후 FE-03(#20)에서 바뀐 것은
CSRF 부착·오류 분류·회원가입 화면·스타일 기반이다.

- 빌드: Vite 8.3.8 + TypeScript 6.0.3, React 19.3.1, React Router 7.18.5(data 모드), Axios 1.21.1. 의존성 버전·선정 근거는
  [regression-log.md](regression-log.md). Node `^24.15.0`(`engines`), `.nvmrc` 24.
- 개발 서버: 5173 `strictPort`, `/api` proxy 대상은 저장소 루트 `.env`의 `FRONTEND_API_PROXY_TARGET`(없으면 `http://localhost:8080`,
  예시는 루트 `.env.example`). `VITE_` 변수는 쓰지 않는다.
- 스크립트: `lint`(ESLint), `typecheck`(`tsc -b --noEmit`: app·test·node 3개 tsconfig), `test`(Vitest, jsdom), `build`(`tsc -b` → `vite build` →
  `node scripts/checkDist.ts`).
- API Client(`src/shared/api/apiClient.ts`): `createApiClient()`가 Axios 인스턴스를 감싼다. 인스턴스 보안 기본값 `AXIOS_SECURITY_DEFAULTS`
  (`baseURL: '/api'`, `allowAbsoluteUrls: false`, `withXSRFToken: false`, XSRF 쿠키·헤더 이름 빈 값, `withCredentials: false`, XHR 어댑터),
  `clarifyTimeoutError`. 요청 옵션은 `ApiRequest`(method·path·authScope 필수, timeoutMs·signal·data·params)뿐이다. 경로 검증
  `toApiPath`, Timeout 검증 `resolveTimeoutMs`(기본 10000ms, 1~60000 정수), 오류 `ApiError.failure`(`http`(+ `retryAfterSeconds`)·`invalid-response`·
  `network`·`timeout`·`csrf-unavailable`·`canceled`), `parseRetryAfterSeconds`(1 이상 안전 정수 초만), 응답 형식 검증은 선택 인자 `parse(data, status)`(응답 상태 코드도 받음, 동기·비동기, 실패는 `invalid-response`이되 세대가 바뀌었으면 `canceled`, 파싱 뒤 세대를 다시 확인).
  ProblemDetail 판별은 `Content-Type`의 미디어 타입(매개변수 앞)이 정확히 `application/problem+json`일 때만. session 요청은 시작 세대를 기억해 세대가 바뀌면 결과를
  `canceled`로 버리고, 401 `AUTH_UNAUTHENTICATED`이면 `AuthBridge.reportSessionLost(세대)`만 호출한다(이동 없음).
- CSRF(`src/shared/api/csrfToken.ts` + `apiClient.ts`): POST·PUT·PATCH·DELETE이면 `instance.getUri`로 만든 최종 URL을
  `isCsrfTargetAllowed`(같은 출처·`/api/`)로 확인하고(아니면 `ApiRequestError`), `createCsrfTokenSource`의 `obtain`으로 토큰을 받아
  `X-XSRF-TOKEN`을 내부에서 붙인다. `obtain`은 쿠키를 읽고, 없거나 복구가 필요하면 `GET /api/v1/auth/csrf`를 single-flight로 호출한다
  (개별 signal 취소는 그 요청만 빠짐, 끝나면 공유 Promise를 비움, 발급 뒤 쿠키가 없으면 `csrf-unavailable`). 기다린 뒤 세대를 다시 확인한다.
  복구 요구는 번호로 센다: 403 `AUTH_CSRF_INVALID`를 받으면 `markInvalid(보낸 토큰)`이 번호를 올리고(쿠키가 이미 다른 값이면 무시),
  발급은 시작할 때 번호를 기억해 성공하면 그 번호까지 복구로 본다. 진행 중 발급이 최신 번호를 덮지 못하면 그 발급 뒤에 새 발급을 이어 붙인다.
  실패한 발급은 복구 요구를 남긴다. 세대가 바뀐 응답도 `markInvalid`를 먼저 거친 뒤 `canceled`로 버린다(재전송 없음).
- 오류 표시 분류(`errorPresentation.ts`): forbidden(403, CSRF 제외)·retry-later(503 `AUTH_SESSION_STORE_UNAVAILABLE`)·common(`csrf-unavailable` 포함)·
  caller·csrf-retry(403 `AUTH_CSRF_INVALID`)·silent. `classifyInitFailure`는 `csrf-unavailable`을 `unexpected` initError로 둔다.
- 회원가입(`src/features/signup/`): `signupValidation.ts`(Backend 규칙 재현 범위는 [FE-D031](decisions.md#1-확정-결정), 비밀번호 길이 상수는 [FE-D033](decisions.md#1-확정-결정)(MB-D026), `javaStrip`·
  `normalizeUsername`·필드별 검증, 이메일은 마지막 `@` 기준·local part의 따옴표 조각과 그 안의 백슬래시 이스케이프 허용), `signupApi.ts`(`submitSignup`, 상태 201과 본문을 함께 검사),
  `SignupForm.tsx`(React state, blur·재검사·제출 시 전체, 서버 오류 코드별 고정 문구, 코드별 안내는 계약 상태와 함께(중복 409, 유출 400), CSRF가 아닌 403도 화면에 머물러 공통 배너([FE-D032](decisions.md#1-확정-결정)), 동기 플래그로 제출 1건, 언마운트 시 요청 취소·타이머 정리, 비밀번호 토글은 글자 "표시" 고정·`aria-pressed`·눌림 스타일 `togglePressed`).
  서버 필드 오류는 제출 때 값과 지금 값을 Backend가 판정하는 값(username 소문자, 이메일 strip·소문자, 닉네임 strip, 비밀번호 원문)으로
  비교해 바뀌지 않은 필드에만 붙인다(비밀번호 오류는 아이디에도, 확인 오류는 비밀번호에도 의존, 유출 비밀번호는 비밀번호만). 붙일 오류가 없으면
  `role="status"` 고정 안내만 보인다. 붙인 서버 오류는 의존 필드를 `serverDepsRef`에 함께 기억해, 입력이 그 의존 필드를 바꾸면 지우고,
  클라이언트·서버 오류가 표시됐던 필드는 입력마다 다시 검사한다. 서버 오류가 오면 포커스는 표시 중인 클라이언트 오류와 합친 목록의 첫 필드로 옮긴다. 이벤트 처리는 최신 값·오류 ref(`valuesRef`·`clientErrorsRef`·`serverErrorsRef`) 위에 쌓고,
  오류 변경은 입력·blur·제출·서버 응답 모두 `updateErrors` 한 경로로 한다. 오류 요약은 개수 없는 고정 문구 `role="alert"`이고 실패할 때마다
  새 요소로 다시 알리며, 오류가 0건이 되면 꺼진다(다음 제출 실패까지 다시 켜지지 않음). 429는 마감 시각을 기억하고 `setTimeout` 최대값(2^31-1ms) 이하로 나눠
  남은 시간을 다시 계산하며, 마감 전에는 버튼과 submit 이벤트를 모두 막는다. 화면은 `src/app/pages/SignupPage.tsx`(성공 시
  `/signup/complete`로 replace, state `{ signupCompleted: true }`)와 `SignupCompletePage.tsx`(state 없으면 `/signup`으로 replace),
  배치는 `AuthLayout.module.css`.
- 스타일: `src/styles/tokens.css`(1장 토큰 표, `prefers-color-scheme: dark`)를 `main.tsx`에서 불러오고, 화면은 CSS Modules를 쓴다.
  `index.html`에 `color-scheme` meta.
- 인증(`src/features/auth/`): 순수 Reducer `authReducer`, `AuthProvider`(Context + useReducer, 초기화 Deadline 15000ms, 요청 ID별
  AbortController, StrictMode에서 이전 초기화 취소, 진행 중인 재시도가 있으면 `retry`를 동기적으로 무시, 초기화가 결과로 끝난 뒤에는
  adapter가 바뀌어도 재초기화하지 않음 — 초기화 중 adapter·deadlineMs가 바뀌면 커밋 시점(layout effect)에 기록한 설정 토큰과 다른 이전 요청의 결과를 버리고
  새 설정으로 다시 시작, Suspense 숨김에도 진행 중 초기화를 유지, [FE-D016](decisions.md#1-확정-결정)), `createSessionScope`(세대·세대별 AbortController·세션 상실 중복 방지, 리스너 1개),
  `classifyInitFailure`, `unavailableAuthAdapter`(네트워크 없음, 항상 미인증), `RequireAuth`, `safeRedirect`.
  로그아웃·새 로그인(인증 주체 변경) 이벤트는 BE-03 계약 전이라 만들지 않았다.
- 라우팅(`src/app/router.tsx`): `/`(공개, 가입 링크), `/login`(준비 중, 세션 만료 안내, 가입 링크), `/signup`·`/signup/complete`(공개, FE-U10),
  `/forbidden`, `RequireAuth` 아래 `/app`, `*`(404),
  루트 `errorElement`(`RouteError`, 오류 상세 미노출).
- 빌드 검사: `scripts/forbidTestModules.ts`(Vite build 플러그인, `enforce: 'pre'`, `src/test`·`*.test.*`·`msw` 모듈 load 시 실패. `?raw`·`?url` 포함),
  `scripts/checkDist.ts`(dist에서 가안 표지·경로 검색).
- 테스트: 수와 대응은 [test-spec.md](test-spec.md) 2장, 실행 결과는 [regression-log.md](regression-log.md).

## 3. 공개 API

- 다른 모듈이 import하는 공개 API는 없다. frontend 안에서 화면 코드가 쓰는 진입점은 `app/apiClient.ts`의 `apiClient`, `features/auth`의
  `useAuth`·`RequireAuth`, `features/signup`의 `SignupForm`(client 주입), `shared/api`의 `ApiRequest`·`ApiError`·`presentApiFailure`다.

## 4. 다른 모듈 의존

| 대상 | 의존 내용 | 근거 |
| --- | --- | --- |
| Backend 공통 오류 응답 | ProblemDetail + `code`·`errors[]`, 401 `AUTH_UNAUTHENTICATED`, 503 `AUTH_SESSION_STORE_UNAVAILABLE` ([docs/global/module.md](../global/module.md)) | [G-D003](../policy.md#3-공통-결정-이력), [G-D007](../policy.md#3-공통-결정-이력) |
| Backend 인증 계약 | 미확정. 확정 전까지 어댑터 뒤에 둔다 | [FE-U02](decisions.md#2-미결) |
| Backend CSRF | `GET /api/v1/auth/csrf`(204), `XSRF-TOKEN` 쿠키·`X-XSRF-TOKEN` 헤더, 403 `AUTH_CSRF_INVALID` | [G-D014](../policy.md#3-공통-결정-이력) |
| Backend 회원가입 | `POST /api/v1/auth/signup` 요청·응답·오류 코드([member module.md](../member/module.md) API 표), 검증 규칙(MB-D004·D005·D008) | [FE-D022](decisions.md#1-확정-결정), [FE-D031](decisions.md#1-확정-결정) |

## 5. 데이터 모델

- 서버 데이터 모델은 없다. 클라이언트 전역 상태는 인증 상태 `AuthState<P>` 하나이고, 회원 정보 `P`의 필드는 Backend 계약(FE-U02) 전이라
  정하지 않았다([FE-D009](decisions.md#1-확정-결정)).
