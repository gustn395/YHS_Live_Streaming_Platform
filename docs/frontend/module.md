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
| 인증 어댑터 | 인터페이스 + 미연결 어댑터(운영·일반 개발) + 가안 Mock(테스트 전용) | [FE-D005](decisions.md#1-확정-결정) |
| 인증 상태 | Context + useReducer, Discriminated Union 3가지 상태 + initError, 순수 Reducer | [FE-D009](decisions.md#1-확정-결정) |
| 401·오류 처리 | 요청별 인증 영향 범위 필수 지정, 인증 세대·초기화 요청 ID, 선택적 취소 | [FE-D010](decisions.md#1-확정-결정), [FE-D011](decisions.md#1-확정-결정) |
| 라우팅 | React Router v7 data 모드, 공개·인증 필요 레이아웃, Auth Guard, 라우팅 오류 전용 `errorElement` | [FE-D012](decisions.md#1-확정-결정) |

## 2. As-Is

2026-11-26, 브랜치 `feature/11-frontend-auth-foundation` 작업 트리 기준(기준 커밋 5f46869, 커밋 전).

- 빌드: Vite 8.3.8 + TypeScript 6.0.3, React 19.3.1, React Router 7.18.5(data 모드), Axios 1.21.1. 의존성 버전·선정 근거는
  [regression-log.md](regression-log.md). Node `^24.15.0`(`engines`), `.nvmrc` 24.
- 개발 서버: 5173 `strictPort`, `/api` proxy 대상은 저장소 루트 `.env`의 `FRONTEND_API_PROXY_TARGET`(없으면 `http://localhost:8080`,
  예시는 루트 `.env.example`). `VITE_` 변수는 쓰지 않는다.
- 스크립트: `lint`(ESLint), `typecheck`(`tsc -b --noEmit`: app·test·node 3개 tsconfig), `test`(Vitest, jsdom), `build`(`tsc -b` → `vite build` →
  `node scripts/checkDist.ts`).
- API Client(`src/shared/api/apiClient.ts`): `createApiClient()`가 Axios 인스턴스를 감싼다. 인스턴스 보안 기본값 `AXIOS_SECURITY_DEFAULTS`
  (`baseURL: '/api'`, `allowAbsoluteUrls: false`, `withXSRFToken: false`, XSRF 쿠키·헤더 이름 빈 값, `withCredentials: false`, XHR 어댑터),
  `clarifyTimeoutError`. 요청 옵션은 `ApiRequest`(method·path·authScope 필수, timeoutMs·signal·data·params)뿐이다. 경로 검증
  `toApiPath`, Timeout 검증 `resolveTimeoutMs`(기본 10000ms, 1~60000 정수), 오류 `ApiError.failure`(`http`·`invalid-response`·
  `network`·`timeout`·`canceled`), 응답 형식 검증은 선택 인자 `parse`. session 요청은 시작 세대를 기억해 세대가 바뀌면 결과를
  `canceled`로 버리고, 401 `AUTH_UNAUTHENTICATED`이면 `AuthBridge.reportSessionLost(세대)`만 호출한다(이동 없음).
- 오류 표시 분류(`errorPresentation.ts`): forbidden(403)·retry-later(503 `AUTH_SESSION_STORE_UNAVAILABLE`)·common·caller·silent.
- 인증(`src/features/auth/`): 순수 Reducer `authReducer`, `AuthProvider`(Context + useReducer, 초기화 Deadline 15000ms, 요청 ID별
  AbortController, StrictMode에서 이전 초기화 취소), `createSessionScope`(세대·세대별 AbortController·세션 상실 중복 방지, 리스너 1개),
  `classifyInitFailure`, `unavailableAuthAdapter`(네트워크 없음, 항상 미인증), `RequireAuth`, `safeRedirect`.
  로그아웃·새 로그인(인증 주체 변경) 이벤트는 BE-03 계약 전이라 만들지 않았다.
- 라우팅(`src/app/router.tsx`): `/`(공개), `/login`(준비 중, 세션 만료 안내), `/forbidden`, `RequireAuth` 아래 `/app`, `*`(404),
  루트 `errorElement`(`RouteError`, 오류 상세 미노출).
- 빌드 검사: `scripts/forbidTestModules.ts`(Vite build 플러그인, `src/test`·`*.test.*`·`msw` 모듈 load 시 실패),
  `scripts/checkDist.ts`(dist에서 가안 표지·경로 검색).
- 테스트: 수와 대응은 [test-spec.md](test-spec.md) 2장, 실행 결과는 [regression-log.md](regression-log.md).

## 3. 공개 API

- 다른 모듈이 import하는 공개 API는 없다. frontend 안에서 화면 코드가 쓰는 진입점은 `app/apiClient.ts`의 `apiClient`, `features/auth`의
  `useAuth`·`RequireAuth`, `shared/api`의 `ApiRequest`·`ApiError`·`presentApiFailure`다.

## 4. 다른 모듈 의존

| 대상 | 의존 내용 | 근거 |
| --- | --- | --- |
| Backend 공통 오류 응답 | ProblemDetail + `code`·`errors[]`, 401 `AUTH_UNAUTHENTICATED`, 503 `AUTH_SESSION_STORE_UNAVAILABLE` ([docs/global/module.md](../global/module.md)) | [G-D003](../policy.md#3-공통-결정-이력), [G-D007](../policy.md#3-공통-결정-이력) |
| Backend 인증 계약 | 미확정. 확정 전까지 어댑터 뒤에 둔다 | [FE-U02](decisions.md#2-미결) |

## 5. 데이터 모델

- 서버 데이터 모델은 없다. 클라이언트 전역 상태는 인증 상태 `AuthState<P>` 하나이고, 회원 정보 `P`의 필드는 Backend 계약(FE-U02) 전이라
  정하지 않았다([FE-D009](decisions.md#1-확정-결정)).
