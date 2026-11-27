# frontend — regression-log

실제로 실행한 테스트 결과만 적는다. 자동 테스트와 수동 확인(M-01)은 구분해 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 실행 기록

### 2026-11-26 FE-01(Issue #11) 구현 — 브랜치 `feature/11-frontend-auth-foundation`, 커밋 전 작업 트리

환경: Windows 10, Node v24.19.0, npm 11.17.0.

**의존성 선정(FE-D008)** — 설치 전 2026-11-26 `npm view`로 확인. 기준: 2026-11-12 이전 공개(14일).

| 패키지 | 버전 | 공개일 | 확인 |
| --- | --- | --- | --- |
| axios | 1.21.1 | 2026-11-09 | engines·peer 없음 |
| react / react-dom | 19.3.1 | 2026-10-14 | react-dom peer `react ^19.3.1` |
| react-router | 7.18.5 | 2026-10-27 | engines `node >=20`, peer `react >=18` |
| vite / @vitejs/plugin-react | 8.3.8 / 6.3.0 | 2026-11-11 / 2026-11-09 | engines `^20.19.0 \|\| >=22.12.0`, plugin peer `vite ^8` |
| typescript | 6.0.3 | 2026-04-16 | typescript-eslint peer `<6.1.0` 안 |
| vitest | 5.1.0 | 2026-11-02 | engines `^24.0.0` 포함, peer `vite ^8` |
| msw | 3.0.5 | 2026-11-11 | engines `>=22.12.0` |
| jsdom | 31.0.0 | 2026-11-05 | engines `^24.15.0` |
| @testing-library/react / dom / jest-dom / user-event | 16.3.3 / 10.4.2 / 7.0.1 / 14.6.10 | 08-27 / 09-13 / 08-09 / 11-12(04:41Z) | user-event 14.6.10은 설치 시각 기준 14일 경과 |
| eslint / @eslint/js | 10.14.0 / 10.0.1 | 2026-10-30 / 2026-02-06 | @eslint/js peer `eslint ^10` |
| typescript-eslint / globals | 8.72.2 / 17.14.0 | 2026-11-09 / 2026-11-01 | |
| eslint-plugin-react-hooks / react-refresh | 7.1.1 / 0.5.7 | 2026-04-17 / 2026-09-14 | peer `eslint ^10` 포함 |
| @types/react / react-dom / node | 19.3.0 / 19.3.0 / 24.19.2 | 09-09 / 09-09 / 10-09 | @types/node는 Node 24 계열 |

- 전이 의존성: `npm install --package-lock-only --before=2026-11-12T09:27:00Z --ignore-scripts`로 2026-11-12 이전 버전만 해석해 lockfile을
  만들었다(294개). 설치 스크립트가 있는 패키지는 `fsevents`(macOS 전용)뿐. `npm ls --all` exit 0.
- `--force`, `--legacy-peer-deps`, `npm audit fix`는 쓰지 않았다.

**npm audit — 미해결(FE-D015 위험 수용)**

- 결과: moderate 9, high 0, critical 0. 원인 권고 1건: `minimatch` [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249)
  (`>=7.0.0 <11.0.1`, 설치 10.2.6). 경로: `eslint`, `@eslint/config-array`, `@typescript-eslint/*`(typescript-estree·parser·utils·
  type-utils·eslint-plugin), `typescript-eslint`. 모두 개발 전용 lint 체인이고 운영 번들에 없다.
- 수정 버전 `eslint` 10.15.0(2026-11-13)·`typescript-eslint` 8.74.0(2026-11-23)은 14일 미만이라 올리지 않았다(후속은 [FE-D015](decisions.md#1-확정-결정)).

**FE-T001 (2026-11-26 18:57 KST)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` | exit 0 (audit 경고는 위와 같음) |
| `npm run lint` | exit 0, 오류·경고 0 |
| `npm run typecheck` (`tsc -b --noEmit`) | exit 0. app 23파일, test(테스트·src/test·scripts 테스트), node(`vite.config.ts`·scripts) 3개 tsconfig. 프로젝트 TS 파일 중 어느 tsconfig에도 없는 파일 0개 |
| `npm test` | exit 0, 16파일 193건 통과, 실패 0 |
| `npm run build` | exit 0, 경고 0, `dist/assets/index-*.js` 378.93 kB(gzip 122.73 kB), `check-dist: 가안 표지 없음`. dist에 `msw`·`PROVISIONAL`·`/src/test/`·`setupServer` 문자열 없음(grep) |

- Vite 설정의 `./scripts/forbidTestModules` 확장자 없는 import에 대해 Vite 8 native 설정 로더 경고가 나와 `.ts`를 붙였고, 다시 실행한 lint·
  typecheck·build는 exit 0, 경고 0.

**TDD RED → GREEN 기록**

| 순서 | 대상 | RED(실행 결과) | GREEN |
| --- | --- | --- | --- |
| 1 | FE-T005·T006·T003 일부 | `urlGuard`·`requestOptions` 모듈 없음으로 2파일 실패 | 31건 통과 |
| 2 | FE-T002·T003·T004·T005 | `apiClient` 모듈 없음으로 실패 | 44건 통과(대조군: 기본 Axios는 jsdom에서 `X-XSRF-TOKEN` 첨부, 공통 Client는 미첨부) |
| 3 | FE-T007·T009·T011·T014 | 12건 실패(`attachAuthBridge is not a function`, 브리지 없는 session 요청이 전송됨) | 64건 통과. `signals.test.ts` 8건은 2단계에서 먼저 작성한 `signals.ts`의 검증이라 RED 없이 처음부터 통과 |
| 4 | FE-T017·T010·T009·T008(Reducer) | `authState` 모듈 없음 | 16건 통과 |
| 5 | FE-T008·T009·T010·T011·T014·T015 일부 | `AuthProvider`·`sessionScope`·`classifyInitFailure` 모듈 없음 | 51/53 → 테스트 기대값 오류 수정(아래 S4) 뒤 53건, 3회 연속 통과 |
| 6 | FE-T012·T013, FE-D010 오류 표시 | 4파일 모듈 없음 | 전체 154건 통과 |
| 7 | FE-T016·T015 | ESLint 설정 없음(25건 실패), 빌드 검사 모듈 없음(2파일) | 37건 통과 |
| 8 | 리팩터링: 세션 상실 구독(`react-hooks/refs` lint 오류 해소) | 4건 실패(`subscribeSessionLost is not a function`) | 전체 193건 통과 |
| 9 | 자체 리뷰 지적: `safeRedirect` Open Redirect 우회(FE-T012) | 4건 실패(`/.//evil.example`·`/x/..//evil.example`·`/%2e//evil.example`이 `//evil.example` 반환, `/app/./x` 허용) | 19건 통과, 전체 198건 통과 |
| 10 | 자체 리뷰 지적: `AuthProvider` 초기화 성공 뒤 `markAuthenticated(epoch)`가 false면 dispatch 없이 끝나 Deadline 보장이 깨지는 분기 | RED 없음 — 전용 회귀 테스트를 만들지 못했다(초기화 중에는 세대가 바뀌지 않아 현재 구조에서 도달 경로가 없음) | `INIT_FAILED(unexpected)`를 내도록 수정. **이 분기는 미검증**이며 전체 회귀(198건)만 통과 |

- FE-T006 변이 확인: `ApiRequest.authScope`를 선택 필드로 바꾸자 `tsc -b`가 `TS2578 Unused '@ts-expect-error'`로 실패했고, 원래대로
  되돌리자 exit 0.
- 원인 분석: 5단계의 실패 2건은 상태 로그에 초기 상태(요청 ID 0)와 `INIT_STARTED` 뒤 상태(요청 ID 1)가 각각 `initializing`으로
  남아 기대값과 달랐던 것이다. 제품 동작(세션 상실 전이 1회, epoch 1→2)은 맞았고, 단언을 "인증 뒤 전이 1회"로 고쳤다.
- 구현 중 같은 단계에서 고친 비의도 실패: `apiClient.ts`의 `ApiRequestError` import 누락(ReferenceError),
  `exactOptionalPropertyTypes`로 `params`·`signal`에 undefined 전달(tsc), `isCurrentInit` 타입 가드 누락(tsc), msw 3의 `listen`
  옵션 이름 변경(`onUnhandledRequest` → `onUnhandledFrame`, tsc), node 환경 테스트에서 공용 setup의 `document` 접근.

**미실행·미검증**

- M-01: 아래 "M-01 수동 확인" 절에 기록(2026-11-26 실행).
- Backend `:app:build`, Gradle clean build: **미실행(Backend 변경 없음, FE-01 구현 승인 조건)**.
- 개발 서버의 실제 Backend proxy 연결: 미실행.

**자체 리뷰 반영 뒤 재검증 (2026-11-26 19:13 KST)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` | exit 0 (audit moderate 9 그대로, FE-D015) |
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test` | exit 0, 16파일 198건 통과 |
| `npm run build` | exit 0, 경고 0, `check-dist: 가안 표지 없음` |

**기준 갱신 뒤 재검증 (2026-11-26 19:19 KST, HEAD 5f46869)**

- `git merge --ff-only origin/main`(81e78c8 → 5f46869, 겹치는 파일 없음) 뒤 실행.

| 명령 | 결과 |
| --- | --- |
| `npm ci` | exit 0 (audit moderate 9 그대로, FE-D015) |
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test` | exit 0, 16파일 198건 통과 |
| `npm run build` | exit 0, 경고 0, `check-dist: 가안 표지 없음` |

**M-01 수동 확인 — 실제 브라우저 (2026-11-26 19:27 KST, HEAD 5f46869 + 작업 트리)**

- 환경: Chrome 151(Windows, 확장 도구로 조작), `npx vite --port 5173 --strictPort` 개발 서버. Backend는 띄우지 않았다.
- 절차: 페이지 `http://localhost:5173/`에서 `document.cookie = 'XSRF-TOKEN=m01-check; path=/'` → 페이지의 `XMLHttpRequest.prototype.setRequestHeader`를
  감싸 실제로 설정되는 요청 헤더를 기록 → `import('/src/app/apiClient.ts')`의 `apiClient.request({ method: 'get', path: '/v1/m01-probe', authScope: 'none' })`
  → 대조군으로 Vite가 번들한 기본 Axios(`axios.create({ adapter: 'xhr' })`)로 `/api/v1/m01-control` 요청.
- 결과: 공통 Client 요청 헤더는 `Accept`뿐, `X-XSRF-TOKEN` 없음. 대조군 요청에는 `X-XSRF-TOKEN: m01-check`가 붙었다. 두 요청 모두 Vite proxy
  `/api/...`로 나갔고 Backend가 없어 502(`http proxy error: /api/v1/m01-probe`, `/api/v1/m01-control`).
- 판정: **PASS(수동)**. 공통 Client는 실제 브라우저에서 XSRF 쿠키가 있어도 헤더를 붙이지 않는다.
- 한계: Network 탭 기록은 도구의 추적이 요청 뒤에 시작돼 남지 않았다. 헤더는 XHR `setRequestHeader` 호출 기록으로 확인했다(브라우저가 추가하는
  헤더는 대상이 아니다). 운영 빌드(`vite preview`)는 `/src`를 불러올 수 없어 개발 서버로만 확인했다. 확인 뒤 쿠키를 지우고 탭·개발 서버를 종료했다.

### 2026-11-26 CodeRabbit 리뷰 반영 — 기준 커밋 ecb851f, 커밋 전 작업 트리

| 지적 | RED(실행 결과) | GREEN |
| --- | --- | --- |
| `forbidTestModules`에 `enforce: 'pre'` 없음 | `?raw`·`?url`로 `src/test` 모듈을 들여오면 빌드 성공(2건 실패: "promise resolved … instead of rejecting") | `enforce: 'pre'` 추가 뒤 빌드 실패로 차단 |
| ProblemDetail `includes` 판별 | `application/json; profile="application/problem+json"` 등 3건을 ProblemDetail로 처리 | 미디어 타입 정확 비교, `; charset=utf-8`·대소문자는 허용 |
| 비동기 `parse` 미대기 | reject가 `Error: 형식 오류`로 그대로 전파, 파싱 중 세대 전환에도 결과 반환("요청이 성공했다") | `await parse` + `invalid-response` 변환 + 파싱 뒤 세대 재확인(`canceled`) |
| `retry` 중복 호출 | 같은 렌더의 `retry`를 두 번 부르면 요청 ID 불일치로 `initializing`에 머묾(무한 로딩 재현) | 진행 중 재시도 동기 차단. 변이 확인: 차단 줄을 지우면 이 테스트가 실패 |
| 초기화 뒤 adapter 교체 시 재초기화(minor) | 새 adapter가 다시 호출되고 scope 세대만 올라 세션 상실 401 뒤에도 인증 상태가 남음 | 초기화가 결과로 끝나면 재초기화하지 않음. 초기화 중 교체는 새 adapter로 시작(테스트 추가) |

| 자체 리뷰(Bot 반영분): 세대가 바뀐 뒤 parse가 reject하면 `invalid-response` | 1건 실패(`expected { kind: 'invalid-response' } … { kind: 'canceled' }`) | catch에서도 세대를 먼저 확인해 `canceled`(FE-D011) |
| 자체 리뷰(Bot 반영분): Deadline 뒤 재시도 테스트 없음 | RED 없음 — 기존 코드가 이미 허용(`finish()`가 진행 중 표시를 비움)을 확인하는 테스트로 처음부터 통과 | 테스트 추가 |

- 재검증 결과는 아래 "Bot 리뷰 반영 뒤 재검증" 절.

**Bot 리뷰 반영 뒤 재검증 (2026-11-26 19:51 KST, 기준 커밋 ecb851f + 작업 트리)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` | exit 0 (audit moderate 9 그대로, FE-D015) |
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test` | exit 0, 17파일 213건 통과 |
| `npm run build` | exit 0, 경고 0, `check-dist: 가안 표지 없음` |

- M-01은 XSRF 설정(`AXIOS_SECURITY_DEFAULTS`)을 바꾸지 않아 다시 실행하지 않았다(19:27 결과 유지).

**자체 리뷰 반영 뒤 재검증 (2026-11-26 19:55 KST, 기준 커밋 ecb851f + 작업 트리)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` · `npm run lint` · `npm run typecheck` | exit 0 · exit 0 · exit 0 |
| `npm test` | exit 0, 17파일 215건 통과 |
| `npm run build` | exit 0, `check-dist: 가안 표지 없음` |

### 2026-11-26 CodeRabbit 2차 리뷰 반영 — 기준 커밋 a12d2bd, 커밋 전 작업 트리

| 지적 | RED(실행 결과) | GREEN |
| --- | --- | --- |
| 초기화 중 adapter 교체 시 새 adapter 커밋 뒤·새 effect 전에 끝난 이전 요청이 반영됨 | 이전 adapter가 then 콜백을 잡는 thenable을 돌려주고, AuthProvider 뒤 형제의 `useLayoutEffect`(커밋 중, passive effect 전)에서 완료시킴 → `expected 'authenticated' to be 'unauthenticated'`, 새 adapter 미호출 | adapter 변경 시 `useLayoutEffect` cleanup으로 커밋 시점에 진행 중 초기화를 무효화. 3회 연속 24/24 통과. 변이 확인: 이 줄을 지우면 새 테스트 실패 |
| 자체 리뷰: 위 수정(layout cleanup 취소)은 Suspense·Activity 숨김 때 layout cleanup만 돌아 초기화가 멈출 수 있음 | Suspense로 숨겼다가 다시 보인 뒤 진행 중 초기화를 완료 → `expected 'initializing' to be 'authenticated'` | layout cleanup 취소를 없애고, adapter·deadlineMs·scope 설정 토큰을 layout effect에서 기록해 완료 시 비교 |
| 자체 리뷰: 같은 커밋 창이 deadlineMs 변경에도 열려 있음 | deadlineMs 변경 커밋 직후 이전 요청 완료 → `expected '' to be 'deadline'`(이전 결과가 authenticated로 반영) | 설정 토큰 비교로 함께 차단 |

- 최종 확인: AuthProvider 테스트 3회 연속 26/26 통과. 변이 확인: 토큰 비교를 지우면 커밋 창 테스트 2건(adapter·deadlineMs)이 실패.

**CodeRabbit 2차 반영 뒤 재검증 (2026-11-26 20:59 KST, 기준 커밋 a12d2bd + 작업 트리)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` · `npm run lint` · `npm run typecheck` | exit 0 · exit 0 · exit 0 |
| `npm test` | exit 0, 17파일 218건 통과 |
| `npm run build` | exit 0, 경고 0, `check-dist: 가안 표지 없음` |

- M-01은 XSRF 설정을 바꾸지 않아 다시 실행하지 않았다(19:27 결과 유지).

### 2026-11-26 FE-02(Issue #16) Frontend CI 구성 — 브랜치 `feature/16-frontend-ci-split`, 기준 커밋 b236ffd + 작업 트리(커밋 전)

frontend 코드·의존성 변경 없음. Frontend CI가 실행할 명령(FE-T001, FE-T018)과 audit 판정을 로컬에서 확인했다. Node v24.19.0, npm 11.17.0.

| 명령 | 결과 |
| --- | --- |
| `npm ci` · `npm run lint` · `npm run typecheck` | exit 0 · exit 0 · exit 0 (`npm ci` audit 경고 moderate 9, FE-D015) |
| `npm test` | exit 0, 17파일 218건 통과 |
| `npm run build` | exit 0, `check-dist: 가안 표지 없음` |
| `npm audit --omit=dev --audit-level=high --json` → `npm_audit_report.py --mode blocking` | npm exit 0, 판정 PASS(운영 의존성 취약점 0건) |
| `npm audit --json` → `npm_audit_report.py --mode report` | npm exit 1, 판정 FOUND(moderate 9건, 비차단, FE-D015) |

- GitHub Actions 실행(FE-T018 실검증)은 PR 이후다. 결과는 [fe02-ci-split.md](../testing/fe02-ci-split.md) 6장에 기록한다.

**main 병합 뒤 재검증 (2026-11-26, `272c47a` = 구현 커밋 `5084d00` + `origin/main` `3b0f46a` merge)**

| 명령 | 결과 |
| --- | --- |
| `npm ci` · `npm run lint` · `npm run typecheck` · `npm run build` | 모두 exit 0 |
| `npm test` | exit 0, 17파일 218건 통과 |

**FE-T018 GitHub Actions 실행 (PR #18)**

공통 감지·Gate·Ruleset 결과와 Run 링크는 [fe02-ci-split.md](../testing/fe02-ci-split.md) 6장이 원본이다. 여기에는 Frontend 실행 결과만 적는다.

| 실행 | Frontend 결과 | 근거 |
| --- | --- | --- |
| L3-1 `272c47a` | `frontend-build`(npm ci·lint·typecheck·test·build·audit 2단계) 성공, Artifact 없음, npm 캐시 저장 | 공개 API + 사용자 로그 |
| L3-3 `97d9c68`(감지 FALLBACK) | `frontend-build` 성공. Node 24.22.3, npm 캐시 복원(49,302,846 B, 저장 키와 같음), 차단 audit PASS(전 심각도 0), 전체 audit FOUND(moderate 9, FE-D015) | 사용자 로그 |
| L3-4b `7bf8907`(FE만 실패) | Vitest 5.1.0 17파일 중 1개·218건 중 1건 실패(`safeRedirect.test.ts > 허용: /app`, expected `/app-l3-4b` received `/app`), Build·차단 audit 건너뜀, 전체 audit 실행, `frontend-ci` FAILED | 사용자 화면·로그 + 공개 API |
| L3-4b 원복 `f688b91` | Test·Build·audit 2단계·`Post Set up Node` 성공, `frontend-ci` `NORMAL (PASS)` | 사용자 화면 |

- L3-1 실행의 audit 상세 Summary와 L3-2 원복 실행의 캐시 복원 로그는 미확인이다(같은 기능은 L3-3 실행에서 확인).
- React Router `HydrateFallback` 경고와 Vitest 환경 생성 비용 안내는 L3-4b 실패와 무관하며 FE-02에서 고치지 않았다.

### 2026-11-27 FE-03(Issue #20) 회원가입 화면·CSRF 연동 — 브랜치 `feature/20-frontend-signup`, 기준 커밋 d47736c + 작업 트리(커밋 전)

환경: Windows 10, Node v24.19.0, npm 11.17.0. Worktree `YHS_Live_Streaming_Platform-fe03`. 새 의존성 없음(lockfile 변경 없음).

| 명령 | 결과 |
| --- | --- |
| `npm ci` | exit 0(lockfile 그대로) |
| `npm audit --omit=dev --audit-level=high` | exit 0(운영 의존성 high 이상 0건) |
| `npm audit --json` | moderate 9건, high·critical 0건(FE-D015 위험 수용 대상 lint 체인). `npm audit fix` 미실행 |
| TDD RED(구현 전) | `apiClient.csrf.test.ts` 14건·`errorPresentation.test.ts` 2건·`classifyInitFailure.test.ts` 1건·`signupValidation.test.ts` 50건·`signupApi.test.ts` 10건·`SignupForm.test.tsx` 23건·`SignupPage.test.tsx` 8건이 기능이 없어 실패함을 확인 |
| `npx vitest run` | exit 0, 22파일 338건 통과(shared/api 113, features/auth 87, features/signup 83, app 16, src/test 25, scripts 14) |
| `npm run typecheck` · `npm run lint` | 모두 exit 0 |
| `npm run build` | exit 0, `check-dist: 가안 표지 없음`(JS 393.01 kB, CSS 3.56 kB) |

- 의도된 기존 테스트 변경 1건: `apiClient.test.ts`의 "ProblemDetail의 code·title·detail·errors[]를 꺼낸다"는 POST를 보내므로 준비 단계에 CSRF 쿠키를
  넣었다(이제 POST는 토큰이 없으면 `/csrf`를 먼저 호출한다, FE-D020). 검증 내용은 바꾸지 않았다.
- 미실행: M-02(실제 Backend 수동 확인, 별도 승인 대기), M-01(FE-01부터 미실행), Safari 확인, FE-T018 GitHub Actions(PR 이후),
  Backend `:app:build`(Backend 변경 없음).

**S6 리뷰 반영 뒤 재검증 (2026-11-27, 같은 작업 트리)**

| 명령·확인 | 결과 |
| --- | --- |
| TDD RED(수정 전) | `csrfToken.test.ts` 3건·`apiClient.csrf.test.ts` 1건·`signupValidation.test.ts` 2건·`SignupForm.test.tsx` 8건이 기대한 이유로 실패 |
| 뮤테이션 확인(처음부터 통과한 회귀 테스트) | 제출 가드(`submittingRef`)·대기 시각 가드·언마운트 타이머 정리·언마운트 요청 취소를 하나씩 지우면 각 테스트가 실패함을 확인한 뒤 되돌림. 대기 시각 가드 테스트는 처음에 즉시 단언해 가드를 못 잡아서 200ms 대기 뒤 단언하도록 고침 |
| Backend 이메일 판정 확인 | 로컬 Gradle 캐시의 Hibernate Validator 9.1.6·9.1.3 `EmailValidator`를 저장소 밖 임시 Java 파일로 실행. `"a b"@example.com`·`"a@b"@example.com` VALID, `a@@b.com`·`a b@c.com`·`a@b c.com` INVALID(두 버전 같음). 클라이언트 규칙을 이 결과에 맞춤 |
| `npx vitest run` | exit 0, 23파일 366건 통과(shared/api 121, features/auth 87, features/signup 103, app 16, src/test 25, scripts 14) |
| `npm run lint` | 첫 실행 exit 1(react-hooks purity: 컴포넌트 안 `Date.now()` 2곳) → 시간 계산을 컴포넌트 밖 함수로 옮긴 뒤 exit 0 |
| `npm run typecheck` · `npm run build` | 모두 exit 0, `check-dist: 가안 표지 없음` |

**S6 재리뷰(R1~R5)·후속 확인 반영 뒤 최종 재검증 (2026-11-27, 같은 작업 트리)**

| 명령·확인 | 결과 |
| --- | --- |
| TDD RED(수정 전) | `signupValidation.test.ts` 이메일 4건(점으로 이은 따옴표 조각)·1건(따옴표 안 `\"`), `SignupForm.test.tsx` 2건(모두 낡은 결과 안내 R3, blur 요약 재알림 R2)이 기대한 이유로 실패 |
| RED 미재현(회귀 감시로 둠) | R1·후속(렌더 사이 없는 연속 입력의 값·서버 오류 덮어쓰기), 재제출 뒤 요약 키 미리셋: React가 discrete 이벤트마다 반영하고 입력 change가 먼저 키를 끄므로 DOM 이벤트로 재현되지 않음. 코드는 ref·`updateErrors` 단일 경로로 바꿈 |
| 뮤테이션 확인 | 언마운트 타이머 정리를 지우면 `clearTimeout` spy 테스트가 실패함을 확인 후 되돌림 |
| Backend 이메일 판정 추가 확인 | Hibernate Validator 9.1.6 실행: `"a b".c@x.com`·`c."a b"@x.com`·`"a\"b"@x.com`·`"a\\b"@x.com` VALID, `"a""b"@x.com`·`a"b c"@x.com`·`""@x.com`·`"a\ b"@x.com`·`a\b@x.com`·`a..b@x.com`·`"a"b@x.com` INVALID |
| 간헐 실패 | 중간 실행 1회에 기존 FE-01 테스트 `AuthProvider.test.tsx:271`("오류 상태에서 retry를 연달아 두 번 불러도…")이 `expected 'initializing' to be 'authenticated'`로 실패. 같은 파일 단독 6회·전체 이후 6회는 모두 통과. FE-03은 AuthProvider를 바꾸지 않았다. 부하 시 타이밍 문제로 보고 범위 밖 후속 후보로 남김(PASS로 숨기지 않음) |
| `npx vitest run` | 최종 3회 연속 exit 0, 23파일 379건 통과(shared/api 121, features/auth 87, features/signup 116, app 16, src/test 25, scripts 14) |
| `npm run lint` · `npm run typecheck` · `npm run build` | 모두 exit 0, `check-dist: 가안 표지 없음` |
| 보이지 않는 문자 검사(`src`) | 0건 |

**M-02 실제 Backend 수동 확인 (2026-11-27, 수동 — 자동 결과와 구분)**

환경: Chrome 151.0.7922.138(공식 빌드, 64비트), Windows. Frontend `npm run dev`(localhost:5173, 프록시 대상 18080) — fe03 작업 트리(커밋 전).
Backend: fe03 Worktree의 `backend/`(기준 커밋 `d47736c`), `:app:bootRun --no-daemon`(Java 17 toolchain), 포트 18080. 컨테이너
`postgres:17-alpine`·`redis:7.4-alpine`·`axllent/mailpit:v1.21.0`(`--rm`, 루프백 전용 포트, 비밀번호 없는 구성). 비밀 키 3종은 실행 명령 안에서
`openssl rand`로 만든 임시 값이고 어디에도 기록하지 않았다. 보안 설정·Rate Limit·HIBP는 바꾸지 않았다(HIBP 실제 호출).
Backend 기준을 `origin/main`(`cea2deb`, BE-04 #19 포함)이 아닌 `d47736c`로 둔 근거(사용자 결정 Q1=a): BE-04의 Backend 변경은 refresh·logout API와
`NullAuthenticatedSessionStrategy`(인증된 요청의 XSRF 쿠키 삭제 결함 수정)이고, 회원가입 API·`/csrf` 발급·익명 요청의 CSRF 검증은 바꾸지 않았다
(`git diff d47736c origin/main`의 `GlobalSecurityConfig`·`ApiV1AuthController` 확인). 최신 main 위에서 실행한 것은 아니다.

기동 확인(Claude 실행): health `UP`, Flyway `v20261126.1` 적용, 프록시 경유 `GET /api/v1/auth/csrf` 204 + `Set-Cookie: XSRF-TOKEN=…; Path=/; Secure; SameSite=Strict`, `Cache-Control: no-store`.

| M-02 항목 | 결과(사용자 Chrome 확인) |
| --- | --- |
| 1 가입 201, CSRF 쿠키와 요청 헤더, 완료 화면 | PASS |
| 2 완료 화면 새로고침·뒤로 가기·직접 접근 | PASS |
| 3 `GET /csrf`에 `X-XSRF-TOKEN` 없음(M-01 범위와 같음) | PASS |
| 4 중복 아이디 409: 필드 오류·요약·포커스 | PASS |
| 5 유출 비밀번호 안내 | PASS |
| 6 CSRF 403: 보안 확인 만료 안내·입력 유지·자동 재전송 없음 | PASS(유도 방법 아래) |
| 7 재제출 → `GET /csrf` 204 → `POST /signup` 201 → 완료 화면 | PASS |
| 8 잘못된 이메일의 실제 Backend 400 필드 표시 | 미실행(사용자 생략). 같은 동작은 자동 테스트 FE-T026으로만 검증 |
| 9 폭 320px 가로 스크롤 없음(`scrollWidth <= clientWidth` = true)·다크 모드·대비 | PASS. 구체적인 대비 수치는 기록하지 않음 |
| 추가: 429 대기 안내 | "요청이 많습니다. 약 58분 뒤 다시 시도해 주세요." 표시를 사용자가 확인. 그때 버튼 비활성 여부는 미확인 |

- 429 경위: 가입 IP 카운터가 7(허용 5 + 거부 2로 추정, 모든 요청이 프록시를 거쳐 `127.0.0.1` 하나로 집계)이 되어 한도(시간당 5회, MB-D018)에 걸렸다.
  사용자 결정으로 M-02 전용 Redis의 `ratelimit:signup:*` 키 1개만 지워 시험 데이터를 1회 초기화했다(한도·설정은 그대로).
- CSRF 403 유도: 처음 안내한 "DevTools Override headers"는 요청 헤더를 바꾸지 못해 쓸 수 없었다(안내 오류). Console에서 `path=/signup`인 가짜
  `XSRF-TOKEN` 쿠키를 더해 가입 화면 JS가 가짜 값을 헤더에 싣게 하고(서버에는 `Path=/` 진짜 쿠키만 전달) 403을 유도했다. 복구는 그 쿠키를 지운 뒤
  다시 제출했다. 가짜 쿠키를 지우지 않으면 `/csrf` 재확보 뒤에도 403이 반복된다(같은 이름 쿠키가 여러 Path에 있는 경우, S6 1차 리뷰 보류 항목과 같음).
- 미확인: Safari, CSRF 토큰 값이 갱신됐는지(Backend는 쿠키가 있으면 같은 값을 쓴다), 429 때 버튼 비활성 상태.
- 정리: Frontend·Backend 프로세스 종료(TaskStop 뒤 남은 Java PID 24848·Vite PID 16312를 명령줄로 확인하고 종료), 컨테이너 3개 정지(`--rm`로 삭제),
  포트 18080·5173·15432·16379·11025·18025 해제 확인. 다른 컨테이너는 건드리지 않았다.

### 2026-11-27 FE-03(Issue #20) CodeRabbit 리뷰 반영 — 기준 커밋 acf2c7e + 작업 트리(커밋 전)

대상: CodeRabbit CR-1~CR-10(CR-7 거부·주석만), 결정 [FE-D032](decisions.md#1-확정-결정). 모두 Claude가 실행했고 Backend는 실행하지 않았다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| RED | `npx vitest run src/features/signup src/shared/api/apiClient.test.ts` | 새 테스트 10건 실패(기대한 이유): signupApi 200·202 성공 처리, apiClient parse 상태 코드 미전달, CR-4 토글 글자, CR-3 포커스, CR-5 서버 오류 필드 재검사, CR-6 폼 200, CR-9 400 + 중복 코드, CR-10 2건 |
| 처음부터 통과(RED 아님) | 같은 명령 | CR-2 부재 단언 보강 2건, CR-8 비CSRF 403, CR-10 유출 오류 유지, signupApi 204 |
| GREEN | 같은 명령 | 144건 통과 |
| Mutation | 임시 변경 뒤 해당 테스트만 실행, 파일 복원(`cmp` 일치) | 늦은 오류 필터 끔 → CR-2 테스트 실패. 403에서 배너 없이 종료 → CR-8 실패. 유출 오류 의존에 username 추가 → CR-10 유지 테스트 실패. 상태 201 검사 제거·이어서 null 본문 검사 제거 → 204 테스트는 계속 통과(본문 형식 검사가 따로 막음). 상태 검사 자체는 200·202 RED로 확인 |
| S5 | `npm --prefix frontend run lint` | 종료 0 |
| S5 | `npm --prefix frontend run typecheck` | 종료 0 |
| S5 | `npm --prefix frontend run test -- --run` | 23파일 392건 통과(FE-01 포함 전체) |
| S6 반영 | 제품 코드 주석의 CR 번호를 결정 ID로 교체 뒤 lint·typecheck·test·build | 종료 0, 392건 통과, build 0 |
| S6 재리뷰 Q1=a RED | `npm --prefix frontend run test -- --run src/features/signup/SignupForm.test.tsx -t "유출 코드라도 400이 아니면"` | 1건 실패(기대한 이유: 409 + 유출 코드에 공통 배너 없음) |
| S6 재리뷰 Q1=a GREEN·S5 | lint·typecheck·`test -- --run`·build | 종료 0, 23파일 393건 통과, build 0 |
| S5(최종) | lint·typecheck·`test -- --run`·build, 보이지 않는 문자 검사, `git diff --check` | 종료 0, 23파일 393건 통과, build 0, 0건, 오류 없음 |

- S7(이해도 퀴즈)은 사용자 결정으로 생략했다(질문 1/6만 출제, 답 없음). Skill 게이트 확인은 S7 미완료 1건으로 종료 코드 1이다.
| S5 | `npm --prefix frontend run build` | 종료 0, check-dist 가안 표지 없음 |
| S5 | 보이지 않는 문자 검사(Python, `frontend/src`·`docs/frontend`), `git diff --check` | 0건, 오류 없음 |

- 수 집계용 JSON 실행(`npm --prefix frontend exec -- vitest run --reporter=json`)은 저장소 루트에서 실행돼 Vitest 설정(jsdom)을 읽지 못하고
  167건이 `document is not defined`로 실패했다. 실행 방법 오류라 결과로 쓰지 않고 파일별 테스트 수만 셌다.
- 미실행: CR-1(`overflow-wrap: anywhere`) 긴 아이디 안내 줄바꿈과 토글 눌림 스타일의 브라우저 수동 확인. 실제 Backend 연동(M-02) 재실행.

### 2026-11-27 FE-03(Issue #20) member BE-06 가입 정책 반영 — 기준 커밋 23fc681 + 작업 트리(커밋 전)

대상: [FE-D033](decisions.md#1-확정-결정)(비밀번호 길이 MB-D026, 가입 제한 MB-D027). Backend 계약 근거는 `origin/feature/24-member-signup-policy`
(`dd071d6`, main 미병합)의 `PasswordPolicy`(10~30)·`PasswordViolation`(문구)·`MemberLimitProperties`(10분 20회)를 읽기 전용으로 확인했다.
모두 Claude가 실행했고 Backend는 실행하지 않았다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| RED | `npm --prefix frontend run test -- --run src/features/signup` | 6건 실패(기대한 이유): 10·9자, code point(이모지 9·10·30·31), 30·31자, 공백 포함 10·9자, 오류 문구, 안내 문구 |
| 처음부터 통과(RED 아님) | 같은 명령 | NFC 정규화 뒤 길이(NFD 9·30자), Retry-After 600·601·3600초 3건 |
| Mutation | 임시 변경 뒤 해당 테스트만 실행, 파일 복원(`cmp` 일치) | NFC 정규화 제거 → NFC 테스트 실패. `Math.ceil` → `Math.round` → 601초 테스트 실패. `blockUntil` 제거 → Retry-After 3건 실패 |
| GREEN 첫 전체 실행 | `npm --prefix frontend run test -- --run` | 1건 실패: `src/test/eslintBoundaries.test.ts` "shared → features"(15691ms). 단독 실행 25건 통과, 바로 다시 한 전체 실행 397건 통과. 원인 미확정(이번 변경과 무관한 파일), 간헐 실패로 기록 |
| S5 | `npm --prefix frontend run lint` / `typecheck` | 종료 0 / 종료 0 |
| S5 | `npm --prefix frontend run test -- --run` 2회 | 23파일 397건 통과 2회 |
| S5 | `npm --prefix frontend run build` | 종료 0, check-dist 가안 표지 없음 |
| S5 | 보이지 않는 문자 검사, `git diff --check`, 제한 수치 grep(`20회`·`10분`·`시간당`·`600`, 테스트 제외) | 0건, 오류 없음, 가입 제한 수치 없음(요청 Timeout 주석 1건만) |

- FE-D033 (5) 계약 확인(BE-06 main 병합 뒤): `origin/main` `774b493`(#25)의 Backend는 `dd071d6`과 diff 없음. `PasswordPolicy`는 `PasswordNormalization.nfc` 뒤 `codePointCount`로 10~30, `PasswordViolation` 문구 2개가 Frontend `MESSAGES`와 같음, `MemberLimitProperties` signup은 `Window(20, 10분)`이고 Frontend 코드에는 수치 없음. main은 `d47736c` 이후 Frontend 파일을 바꾸지 않았다. 함께 실행한 Vitest 전체는 위 S5(397건)다.
- 이 브랜치의 `docs/member/decisions.md`에는 MB-D026·D027이 없다(main에만 있음). main 반영(병합·rebase)은 승인 범위 밖이라 하지 않았고, Squash Merge 뒤 main에서 링크가 이어진다.
- S6 반영(문서 3건) 뒤 S5 재실행: lint·typecheck 0, 23파일 397건 통과, build 0, 보이지 않는 문자 0건, `git diff --check` 오류 없음.
- S7(이해도 퀴즈)은 사용자 결정으로 생략했다(질문 1/6만 출제, 답 없음). Skill 게이트 확인은 S7 미완료 1건으로 종료 코드 1이다.
- 미실행: 실제 Backend 연동(FE-D033 (5)에 따라 코드 비교 + Vitest 전체로 대신함), 안내 문구의 브라우저 표시 확인.

### 2026-11-27 FE-04(Issue #26) 로그인·이메일 OTP 화면·로컬 E2E — 브랜치 `feature/26-frontend-login-otp`, 기준 커밋 8953906 + 작업 트리(커밋 전)

대상: [FE-D034](decisions.md#1-확정-결정)~[FE-D040](decisions.md#1-확정-결정). 모두 Claude가 실행했다. 바로 아래 표의 단계는 Backend·Docker 컨테이너 없이 실행했다. 실제 Backend를 쓴 E2E는 그다음 "E2E 실행" 표에 따로 적었다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| 설치 | `npm ci` | 293 packages |
| RED 인증 상태 | `npx vitest run src/features/auth/authState.test.ts` | 5건 실패(이벤트 미처리 → `undefined`) |
| RED Provider | `npx vitest run src/features/auth/AuthProvider.test.tsx` | 5건 실패(`completeLogin is not a function`) |
| RED API 계약 | `npx vitest run src/features/login src/features/otp` | 모듈 없음 → 셸 뒤 24건 실패(`미구현`) |
| RED 공용 대기 | `npx vitest run src/shared/time` | 7건 실패 |
| 리팩터링 확인 | `npx vitest run src/features/signup src/app` | SignupForm을 공용 대기로 바꾼 뒤 기존 테스트 무수정 149건 통과 |
| Mutation | `waitUntil` 완료 조건을 `remaining <= 120_000`으로 임시 변경 뒤 `SignupForm.test.tsx`, 복원 | 429 대기 2건 실패 → 복원 뒤 통과(기존 테스트가 리팩터링 경로를 검증함) |
| RED 로그인 폼·Google 오류 | `npx vitest run src/features/login` | 셸에서 30건 실패 |
| RED OTP 폼 | `npx vitest run src/features/otp/OtpForm.test.tsx` | 셸에서 22건 실패. 구현 뒤 2건 실패: ① `maxLength=4`가 숫자를 거르기 전 붙여넣은 원문을 잘라 `"04"`가 됨(실제 결함, `maxLength` 제거) ② 남은 타이머 1개는 MSW `delay` 타이머(테스트 결함, 검사를 둘로 나눔). 테스트 헬퍼가 가짜 타이머 없이 `advanceTimers`를 넘기던 결함도 수정 |
| RED 페이지·라우터 | `npx vitest run src/app/pages/LoginPage.test.tsx` | 셸에서 13건 실패. 테스트 기대 1건 수정: 안전하지 않은 `from`은 FE-D012대로 `/`(처음 테스트가 `/app`을 기대함) |
| GREEN 정적 검사 | `npm run lint` / `npm run typecheck` | 1차 실패: lint `react-refresh/only-export-components` 3건(컴포넌트 파일의 문구 상수 → `loginText.ts`·`otpText.ts`·`loginPageText.ts`로 분리), typecheck `RequireAuth.test.tsx` Context 값에 `completeLogin` 없음 3건(타입만 추가). 수정 뒤 종료 0 / 0 |
| GREEN 전체 | `npm test` | 30파일 505건 통과 |
| 빌드 | `npm run build` | 종료 0, check-dist 가안 표지 없음 |
| Playwright 설치 | `npm install -D --save-exact @playwright/test@1.65.0` | 1.65.0(2026-11-05 공개, `node >=20`, 의존 `playwright` 1.65.0, peer 없음). 1.65.1은 2026-11-13 공개라 14일 미만(FE-D008)이어서 쓰지 않음. lockfile +46행 |
| audit | `npm audit --omit=dev --audit-level=high` / `npm audit` | 운영 0건 / 전체 moderate 9(모두 기존 FE-D015 lint 체인, Playwright 관련 없음) |
| 브라우저 | `npx playwright install chromium` | 사용자 캐시(`%LOCALAPPDATA%\ms-playwright`)에 설치, 저장소 변경 없음 |
| E2E 구성 확인 | `npx playwright test --list` | 3건(E2E-01~03) |
| E2E 빠른 실패 | 환경변수 없이 / 없는 서버(`127.0.0.1:1`)로 `npx playwright test` | "E2E 환경변수 E2E_BASE_URL가 없습니다…" / "E2E 환경 미기동: Frontend dev 서버에 연결할 수 없습니다…"로 테스트 전 중단 |
| Compose 확인 | `docker compose -p yhs-e2e -f infrastructure/docker/e2e/compose.yml config --quiet`(임시 비밀번호 값 있음/없음) | 종료 0 / `required variable E2E_POSTGRES_PASSWORD is missing a value`. 컨테이너는 띄우지 않음 |
| 이미지 확인 | `docker image inspect`(로컬 이미지) | postgres `/var/lib/postgresql/data`·redis `/data` 익명 볼륨 선언, mailpit 볼륨 없음·내장 healthcheck `/mailpit readyz` |

**E2E 실행 (2026-11-27, 사용자 승인 "E2E 승인합니다", 자동 — Playwright 1.65.0 Chromium)**

환경: fe04 작업 트리(기준 커밋 8953906 + 커밋 전 변경). Backend는 fe04 `backend/` `:app:bootRun --no-daemon`(포트 18080), Frontend `npm run dev`(5173,
프록시 대상 `http://127.0.0.1:18080`), 서버 묶음은 `infrastructure/docker/e2e/compose.yml`(`yhs-e2e`). 비밀 값(DB 비밀번호, JWT·OTP HMAC·pepper 키, 더미
Google ID·Secret)은 Python `secrets`로 만들어 세션 전용 임시 폴더의 파일에만 두었다가(명령이 여러 번이라 같은 값이 필요) 끝난 뒤 지웠다. 값은 출력·기록하지 않았다.
가입은 Backend 설정대로 실제 HIBP를 호출했다. 보안 설정·Rate Limit은 바꾸지 않았다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| 사전 | 포트 15432·16379·11025·18025·18080·5173 확인 | 모두 비어 있음, `yhs-e2e` 프로젝트 없음 |
| 1회차 기동 | `docker compose -p yhs-e2e -f infrastructure/docker/e2e/compose.yml down -v` → `up -d --wait` → bootRun → dev | 3개 healthy, health UP, 프록시 경유 `/csrf` 204 |
| 1회차 | `npm --prefix frontend run e2e`(Full) | **3건 통과**(E2E-01 7.3초, E2E-02 5.6초, E2E-03 1.1분, 합계 1.4분) |
| E2E-ENV 초기화 | bootRun 종료 → `down -v` → `yhs-e2e` 볼륨 0개 확인 → `up -d --wait` → bootRun | Flyway `Successfully applied 1 migration`(빈 DB에서 다시 적용), `/csrf` 204 |
| 2회차 | `npm --prefix frontend run e2e`(Full) | **3건 통과**(9.0초, 6.6초, 1.1분, 합계 1.4분) |
| 정리 | bootRun·dev 종료 → `down -v` → 남은 것 확인 | 컨테이너 0·볼륨 0. 5173에 남은 Vite node 프로세스(fe04 `vite.js` 확인) 1개를 종료한 뒤 포트 6개 모두 비어 있음. 비밀 값 파일 삭제 |

- E2E-ENV: 통과(`down -v`를 사이에 둔 2회 모두 통과, 끝난 뒤 `yhs-e2e` 컨테이너·볼륨 0개). Playwright 결과물은 실패가 없어 남지 않았고 저장소 안에도 없다.
- 관찰: bootRun 뒤 `backend/build-logic/.kotlin/`(Gradle Kotlin 세션 데이터)가 추적 외 파일로 생겼다. `.gitignore`에 없다. 지우지 않았고 커밋 대상이 아니다(후속 검토, 규칙 파일 변경은 승인 필요).
- 미실행: M-03(수동, 실제 Google 로그인 포함, 별도 승인), Safari.

**S6 자체 리뷰 반영 (2026-11-27, 사용자 결정 리뷰 Q1=a·Q2=a·Q3=a, [FE-D042](decisions.md#1-확정-결정))**

S6(Standards·Spec) 지적 12건 중 수용 1·2·4·5·6·8, Q1(로그인·OTP 공용 훅·실패 분류·공통 문구, SignupForm 제외), Q2, Q3를 반영했다. 지적 11은 거부, 12는 PR 본문 대상. S10은 0건.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| RED | `npx vitest run src/app/pages` | 5건 실패(기대한 이유): Q2 `completeLogin` false 안내·다시 로그인 링크 2건(`Unable to find role="alert"`·`role="link"`), Q3 사유 history state 3건(`expected { notice: … } to be null`) |
| 리팩터링·GREEN | `npx vitest run src/app src/features/login src/features/otp src/shared` | 20파일 251건 통과(공용 `formFailure`·`formFeedback`·`FormBanner`·`commonText`, 스타일 `shared/ui`로 이동, `RESEND_INTERVAL_SECONDS` → `otpApi.ts`, 사유 목록 `OTP_RESTART_REASONS`). `formFailure.test.ts`는 추출과 함께 쓴 특성 테스트(처음부터 통과, RED 아님) |
| Mutation | `OtpPage`의 `completeLogin` 결과 확인 제거 뒤 `OtpPage.test.tsx`, 복원 | 1건 실패 → 복원 뒤 2건 통과 |
| 전체 | `npm run lint` / `npm run typecheck` / `npm test` / `npm run build` | 0 / 0 / 32파일 520건 통과 / 0(check-dist 가안 표지 없음) |
| S5 재검증 | 같은 4개 명령, `git diff --check`, 변경 46파일 보이지 않는 문자·줄 끝 공백·비밀 값 패턴 | 0 / 0 / 520건 통과 / 0, 오류 없음, 0건 |
| S6 재리뷰 반영 | 지적 1~6 수용(배너 `FormBanner` 공용 사용, history state 타입 `loginFlowState.ts`, module 60초 → 상수 이름, `retryMinutes` → `shared/time`, 다시 로그인 링크 `from` 유지, 확인 429 테스트에 재발송 활성 단언), 7·9 거부, 8 오탐(`safeRedirect`가 `/login` 하위를 `/`로 바꿈). RED: `npx vitest run src/app/pages/OtpPage.test.tsx` 1건 실패(링크 `href`가 `/login`) → GREEN 3건 | 전체 lint 0, typecheck 0, 32파일 522건 통과, build 0 |
| S5 재검증 중 간헐 실패 → S4 | `npm test`(전체) | 1건 실패: `router.test.tsx` "인증 필요 경로 /app 은 미인증이면 /login?from=/app 으로 간다"(105ms, 상세 메시지는 남기지 않음). 단독 10회·동시 부하 24회·전체 6회 재현 0. 측정: 이 테스트는 평소 49~72ms라 `waitFor` 1초 초과는 아님(기각). 임시 진단 테스트(`[DEBUG-fe04r]`, 삭제 확인)로 "`router.state`가 `/login`이 된 직후 로그인 제목이 DOM에 아직 없는" 경합을 300회 중 3·4·10회 재현, 기다렸다가 찾으면(`findByRole`) 0회 |
| S4 원인·수정 | 테스트만 수정 | 원인: 라우터 상태가 화면 커밋보다 먼저 바뀌는데 테스트가 바로 `getBy`로 동기 조회. 제품 결함 아님. 같은 패턴 3곳을 기다렸다가 찾도록 바꿈: `router.test.tsx:31`, `LoginPage.test.tsx`(인증 표시), FE-03 테스트 `SignupPage.test.tsx:78`. 이번 LoginPage가 이전 Placeholder보다 무거워 드러난 것으로 추정(이전 코드와 비교 측정은 하지 않음) |
| S5 재검증 | `npm run lint` / `typecheck` / `npm test` 3회 / `build`, `git diff --check`, 변경 파일 스캔 | 0 / 0 / 522건 통과 3회 / 0, 오류 없음, 보이지 않는 문자·줄 끝 공백·비밀 값 0 |
| E2E 재실행(사용자 지시) | 위 "E2E 실행"과 같은 절차(새 비밀 값, `down -v` → `up -d --wait` → bootRun → dev → `npm --prefix frontend run e2e`) | **3건 통과**(7.7초, 6.0초, 1.1분, 합계 1.4분). 정리: `down -v` 뒤 컨테이너 0·볼륨 0, 남은 fe04 Vite node 1개 종료, 포트 6개 해제, 비밀 값 파일 삭제 |

### 2026-11-27 FE-04(Issue #26) CodeRabbit 리뷰 반영 — 기준 커밋 2f98e52 + 작업 트리(커밋 전)

인라인 2건을 지금 코드로 확인했고 둘 다 수용했다(S8). 모두 Claude가 실행했다. Backend·Docker는 실행하지 않았다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| CR-2 RED | `npx vitest run src/features/otp/otpApi.test.ts` | 6건 실패(기대한 이유: RFC 1123·시간대 없음·날짜만·오프셋·소수 초 10자리·로그인 성공 응답 RFC 1123을 받아들임). 정상 ISO 3건(`Z`, 소수 3·9자리)은 처음부터 통과 |
| CR-2 GREEN | `parseInstant`가 Backend Instant 형식(`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$`)을 먼저 확인. `npx vitest run src/features/otp src/features/login src/app` | 119건 통과 |
| CR-1 재현 | 연결만 받고 응답하지 않는 임시 로컬 서버(127.0.0.1:18999, scratchpad) 대상으로 `timeout 25 npx playwright test` | 25초 안에 끝나지 않아 강제 종료(rc 124) |
| CR-1 수정 확인 | 점검 `fetch`에 `AbortSignal.timeout(5_000)`, 같은 명령 | 8초 만에 "E2E 환경 미기동: Frontend dev 서버에 연결할 수 없습니다…"로 종료(rc 1). 임시 서버 종료·포트 해제 확인 |
| 검증(S5) | `npm run lint` / `typecheck` / `npm test` / `build`, `git diff --check` | 0 / 0 / 32파일 531건 통과 / 0, 오류 없음 |

| S6·S10 | Standards·Spec 하위 리뷰, 보안 리뷰 | Spec 0건, S10 후보 0건, Standards 판단 3건: 1 FE-T030·로그인 경로 테스트 누락(수용: 테스트 2건 추가 — 공용 파서라 처음부터 통과, 형식 검사를 뺀 뮤테이션에서 2건 실패·복원 뒤 89건 통과), 2 형식 규칙 중복(수용: module은 FE-T031을 가리킴), 3 결정 ID 없음(Q1=a: [FE-D043](decisions.md#1-확정-결정) 기록, FE-T030·T031 근거 갱신) |

- 미실행: 실제 Backend E2E 재실행(이번 변경은 응답 시각 형식 검사와 E2E 사전 점검 시간 제한이며, 실제 Backend의 Instant 형식은 이전 E2E 3회에서 통과함).

### 2026-11-27 FE-04(Issue #26) CodeRabbit 2차 리뷰 반영 — 기준 커밋 faf88f4 + 작업 트리(커밋 전)

인라인 1건(`otpApi.ts:26` 없는 날짜 거부)을 지금 코드로 확인하고 수용했다(S8). Backend·Docker는 실행하지 않았다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| 사실 확인 | `node -e`로 정규식·`Date.parse` 결과 출력 | `2026-02-30`→3월 2일, `2026-04-31`→5월 1일, `2023-02-29`→3월 1일, `T24:00:00`→다음 날 00시로 넘어가 통과. 13월·0월·32일·60분·60초는 이미 NaN으로 거부 |
| RED | `npx vitest run src/features/otp/otpApi.test.ts` | 4건 실패(위 4개 입력이 통과). 윤일 `2024-02-29T23:59:59.999999999Z`와 기존 정상 3건은 통과 |
| GREEN | 해석 결과 `toISOString()` 앞 19자(초 단위)를 입력과 비교. `npx vitest run src/features/otp src/features/login` | 94건 통과 |
| 검증(S5) | `npm run lint` / `typecheck` / `npm test` / `build`, `git diff --check` | 0 / 0 / 32파일 538건 통과 / 0, 오류 없음 |
| S6·S10 | Standards·Spec 하위 리뷰, 보안 리뷰 | S10 0건, Spec 0건(참고: 밀리초를 넘는 소수 초는 엔진마다 다를 수 있음, Safari·Firefox 미검증), Standards 판단 2건(근거 표기 → "FE-D043 (1) 세부"로 수정, throw 반복 → 아래 재구현으로 정리) |
| 엔진 의존 제거(Q1=a, 사용자 보완: 없는 날짜 자동 보정·0~99년) | `node -e` 실측 | `Date.UTC(50, …)` → 1950년(0~99년 함정 확인). 기대값은 구현과 무관하게 `setUTCFullYear`·`setUTCHours`로 미리 계산해 테스트에 고정 |
| RED | `Date.parse`를 NaN으로 바꾼 상태에서 `npx vitest run src/features/otp/otpApi.test.ts` | 6건 실패(정상 시각 6건이 `Date.parse`에 기댐). 범위를 넘는 값 7건(0·13월, 0·32일, 60분·60초, 1900-02-29)은 처음부터 거부 |
| GREEN | 정규식 그룹으로 연·월·일·시·분·초·소수를 꺼내 `new Date(0)`에 `setUTCFullYear`·`setUTCHours`로 넣고(소수는 밀리초로 잘라냄) 넣은 값과 `getUTC*`를 비교, `npx vitest run src/features/otp src/features/login` | 108건 통과 |
| Mutation | ① `setUTCFullYear` → `Date.UTC` ② 자동 보정 비교 끄기, 각각 `otpApi.test.ts` 실행 뒤 복원 | ① 0·50·99년 3건 실패 ② 12건 실패 → 복원 뒤 41건 통과 |
| 검증(S5) | `npm run lint` / `typecheck` / `npm test` / `build`, `git diff --check` | 0 / 0 / 32파일 552건 통과 / 0, 오류 없음 |

- 미실행: 실제 Backend E2E 재실행(정상 Instant 값은 그대로 허용, 이전 E2E 3회에서 통과), Safari·Firefox 확인(이번 재구현으로 엔진의 날짜 문자열 해석에는 기대지 않음).

### 2026-11-28 FE-06(Issue #32) E2E CI 연동 — 브랜치 `feature/32-frontend-e2e-ci`, 기준 커밋 b8b789c + 작업 트리(커밋 전)

로컬 L1·L2만 실행했다(Windows, Python 3.12.4, Node 24.19.0). Docker·Backend·E2E 환경은 띄우지 않았다. 결정·구성 원본은
[fe06-e2e-ci.md](../testing/fe06-e2e-ci.md)와 [FE-D044](decisions.md#1-확정-결정).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| RED 감지 | e2e 분류·출력 테스트 추가 뒤 `python -B -I .github/ci/run_tests.py` | 16건 실패(`Result`에 `e2e` 없음, e2e 경로가 `both`) |
| GREEN 감지 | `detect_changes.py`에 `e2e` 영역·출력 | 113건 통과(기존 판정 테스트는 기대값 그대로, 출력 줄 테스트만 `e2e=run` 추가) |
| RED·GREEN Gate | 3개 대상 조합·`area=e2e`·`E2E_TARGET` 누락 테스트(기존 판정 사례는 e2e 인자만 더함) | 19건 실패 → 121건 통과 |
| RED·GREEN 결과 검증 | `e2e_report.py`(목록 대비 필수 집합) | 모듈 없음 → 143건 통과 → 뮤테이션 생존 2건을 잡는 테스트 2건 추가 |
| RED·GREEN 로그 필터 | `log_filter.py`(spring·compose·playwright Allowlist) | 모듈 없음 → 통과. 고친 것: 테스트 픽스처 공백 1곳, `cause=…Exception`이 긴 토큰으로 가려짐, `(1.4m)` 시간 형식, 빈 줄 |
| GREEN 스텁 검증 | `hibp_stub_check.py` | 통과. 테스트를 먼저 썼지만 RED 실행 결과는 따로 보지 않았다(다른 작업의 뮤테이션 실행을 멈춘 직후). 뮤테이션으로 대신 확인 |
| RED·GREEN 스텁 | `npx vitest run e2e/support/hibpStub.test.ts` | 모듈 없음 → MSW가 요청을 가로채 15건 실패(테스트를 TCP 소켓 요청으로 바꿈) → 응답 chunked로 1건 실패(`Content-Length` 명시) → 17건 통과 |
| Mutation | 감지 3, Gate 2, 결과 검증 7, 로그 필터 10, 스텁 검증 6, 스텁 9(매번 원본 복원) | 37건 모두 실패로 잡힘. 첫 로그 필터 실행은 중간에 멈춰 JWT 규칙 1줄이 지워진 채 남았고, 발견해 복구한 뒤 백업을 남기는 방식으로 다시 실행 |
| L1 Python | `python -B -I .github/ci/run_tests.py` | 182건 통과(기존 98건 포함) |
| L1 Frontend | `npm run lint` / `typecheck` / `npm test` / `build` | 0 / 0 / 33파일 569건 통과 / 0(checkDist 통과) |
| L1 수집 | `npx playwright test --list`, `--grep-invert @slow`, `npm run e2e:smoke -- --list` | 3건(스텁 단위 테스트 미수집) / 2건(E2E-01·02) / 1건(E2E-01) |
| L1 YAML | SchemaStore `github-workflow.json`·`github-action.json`(draft-07) 검증, run 블록 `bash -n`(Git Bash) | 5개 파일 오류 0, run 블록 18개 문법 오류 0·`${{ }}` 없음 |
| 실제 데이터 | 실제 `--list` JSON으로 `e2e_report` 판정, FE-04 로컬 E2E 출력·Backend 로그로 `log_filter` | PR 필수 2·FULL 필수 3 계산, 범위가 맞지 않으면 실패. 결과·집계 줄과 Spring 줄만 남고 그 밖은 가려짐 |
| 스텁 단독 | `node e2e/support/hibpStub.ts`(환경변수 없음 / 있음) | 종료 코드 1과 안내 / `/healthz` 200·통계 `health 1`. Windows Git Bash `kill`은 Node의 SIGTERM 처리기로 가지 않아 종료 코드 143 |
| 기타 | `git diff --check` | 오류 없음 |

- 미실행: L3·L4(GitHub), 필수 체크 전환(FE-U12). Linux 러너에서의 `ss`·`openssl`·`docker compose --wait` 동작과 단계별 시간은
  L3에서 확인한다.

로컬 E2E 회귀(2026-11-28, 사용자 승인 뒤, Windows·Docker 29.8.0·Chromium). 실행마다 새 비밀 값 파일(저장소 밖, 값 미출력),
시작 전·끝난 뒤 `yhs-e2e` `down -v`. 다른 Docker 프로젝트는 건드리지 않았다.

| 회차 | 구성 | 결과 |
| --- | --- | --- |
| 1 | test-spec 3장(dev 5173·`bootRun`·실제 HIBP), `npm run e2e` | 3 passed(1.4m). 첫 Backend 기동은 실행자가 `SPRING_DATASOURCE_PASSWORD`를 넘기지 않아 DB 인증 실패(절차 실수, 코드 결함 아님, CI는 두 변수를 함께 설정) → 넘겨서 다시 기동 |
| 2 | CI와 같은 방식: `:app:bootWar`(실행 WAR 1개) → HIBP 스텁(18099) → `java -jar`(`SERVER_ADDRESS=127.0.0.1`) → `npm run build` → `vite preview --host localhost --port 4173 --strictPort` | 준비 확인: 스텁 `/healthz` 200·health 200·preview 200·프록시 `/api/v1/auth/csrf` 204. 리스너 `127.0.0.1:18080`·`127.0.0.1:18099`·`[::1]:4173`(Windows는 localhost가 ::1) |
| 2 | 목록 JSON → `PLAYWRIGHT_JSON_OUTPUT_FILE` + `--reporter=list,json` 전체 → `log_filter --kind playwright` → `e2e_report --scope full` → `hibp_stub_check --min-ok 3` | 3 passed(1.2m). 필터 출력은 시작·결과 3줄·집계만. report PASS(실행 3·필수 3·@slow 1). 같은 결과를 `--scope pr`로 판정하면 FAIL(범위 밖 실행, PR의 @slow). 스텁 ok 3·unexpected 0·health 1(스텁이 떠 있는 동안 읽음) |
| 2 | PR 범위 `--grep-invert @slow` → `e2e_report --scope pr` → `hibp_stub_check --min-ok 5`(누적) | 2 passed, report PASS(실행 2·필수 2·@slow 0), 스텁 ok 5·unexpected 0 |
| 3 | 단언 3곳을 하나씩 일부러 실패(`sameSite 'Lax'`, 길이 1, 같음 true)시켜 `--grep E2E-0n`, 필터 없는 원본 출력 검사, 매번 복원 | 3건 모두 실패. 출력은 쿠키 속성 4개 차이, `Expected: 1 / Received: 0`, `Expected: true / Received: false`뿐이고 JWT·쿠키 `value`·4자리 OTP 문자열 없음. spec 복원 확인 |
| 정리 | 프로세스 종료(1회차 bootRun·Vite 자식 프로세스는 명령줄로 fe06 Worktree 것임을 확인한 뒤 종료), `down -v`, 실패 trace(OS 임시 `yhs-e2e`)·스텁 통계·비밀 값 파일 삭제 | 컨테이너·볼륨 0개, 15432·16379·11025·18025·18080·5173·4173·18099 닫힘 |

S5·S6(2026-11-28): S5 통과(Python 182, Frontend 0·0·569·0, 수집 3, YAML 5, diff 0) 뒤 S6 Standards·Spec 하위 리뷰에서 반영 10건(R1~R10),
유지 2건(FE-U11 상태·Q4 그 자리 정정은 사용자 결정), 보류 2건(스크립트 중복·감지 필드 묶음). 반영과 로컬 모의 검증:

| 항목 | 명령·방법 | 결과 |
| --- | --- | --- |
| R4 실패 단계 | `failed_step.py` 테스트 추가 → `python -B -I .github/ci/run_tests.py` | RED(모듈 없음) → GREEN 191건. Mutation 3건(취소 무시, 부분 일치 허용, 빈 표시) 모두 실패로 잡힘 |
| R2 대기 시간 | 스텁 대기 루프와 같은 줄을 서버 없는 18099(연결 거부)와 응답 없는 서버(18999)에 실행 | 두 경우 모두 42초에 자체 `::error`로 종료(Step 제한 60초). Backend·preview는 계산으로 최대 약 154초·45초(제한 180·60초) |
| R5 볼륨 확인 | Cleanup과 같은 줄을 로컬 `yhs-e2e`로 실행: 정상 `down -v` / `-v` 없이 `down` | 기동 중 컨테이너 3개, Mounts의 Docker 볼륨 2개, **두 볼륨 모두 프로젝트 라벨 없음**(라벨 검사만으로는 0개로 보여 잔존을 못 잡음). 정상: 남은 볼륨 0·실패 0. 남긴 경우: "볼륨 남음" 2건을 잡음 → 그 2개만 이름으로 삭제. 끝난 뒤 컨테이너·네트워크 0, 포트 닫힘 |
| R1·R3·R6~R10 | 문서·설명·import·이름 수정, 진단 `ps`·`logs`에만 임시 비밀번호 값 | — |
| S5 재검증 | Python / lint / typecheck / Vitest / build / `playwright --list` / YAML 스키마 / run 블록 `bash -n` / `git diff --check` | 191 통과 / 0 / 0 / 33파일 569 / 0 / 3건 / 5개 OK / 18개 OK / 오류 없음 |

- GitHub에서만 확인할 수 있는 것(실제 실패 실행의 실패 단계 기록, 러너의 볼륨 라벨 동작)은 fe06 Q15대로 L3·L4 실제 실행과 FE-U12로 넘긴다.
- S10 보안 리뷰(2026-11-28, 하위 리뷰): 기준(HIGH·MEDIUM, 신뢰도 8 이상)을 넘는 후보 0건. 확인 범위: 실행별 비밀 생성·마스킹·GITHUB_ENV
  (한 줄 값, 마스킹 먼저), `run:`의 PR 입력 사용 없음, GITHUB_OUTPUT 값(정수·고정값), 로그 필터 우회·워크플로 명령 삽입(접두어·`::` 끊기),
  결과·실패 단계·스텁 검증 출력(개수·고정 토큰), 스텁·preview·Backend loopback.
- S7 퀴즈: 미실행(사용자 결정 Q1=b, 수동 예외).

### 2026-11-28 FE-06(Issue #32) CodeRabbit 리뷰 반영 — 기준 커밋 ae0ac44 + 작업 트리(커밋 전)

인라인 2건을 지금 코드와 실제 데이터로 확인했다(S8). Docker·Backend는 실행하지 않았다.

| 단계 | 명령·방법 | 결과 |
| --- | --- | --- |
| CR-1 사실 확인 | 로컬 2회차 실제 목록·결과 JSON(FULL·PR)의 spec `id`·`file`·`line`·`column` 비교 | 세 테스트 모두 목록과 결과에서 같다 → 키에 넣어도 연결이 깨지지 않음. 지적 유효: 기존 키(파일, 제목)는 다른 describe의 같은 제목 테스트를 하나로 합친다 |
| CR-1 RED | 같은 파일·같은 제목·다른 줄(하나만 `@slow`) 테스트, 위치 필드 누락 테스트 추가 → `python -B -I .github/ci/run_tests.py` | 2건 실패("FULL에서 @slow 미실행", 위치 없는 결과 통과) |
| CR-1 GREEN | `_key`를 (id, file, line, column, title)로, 목록·결과가 같은 함수 사용, 형식이 다르면 해석 실패 | 193건 통과. 실제 JSON 재판정 FULL PASS(3·3·@slow 1), PR PASS(2·2·0) |
| CR-1 Mutation | 키를 (file, title) / (file, line, title)로 되돌림 | 1 실패로 잡힘 / 2 통과(이 회귀 테스트는 줄로도 구분됨, id·column은 같은 줄 두 테스트용 보조 구분자) |
| CR-2 경로 확인 | Backend main 로그 호출 검색, 로컬 2회차 실제 Backend 로그 검색, CI 비밀·테스트 비밀번호 형식 확인 | 공백 있는 인용 비밀 값이 필터로 가는 경로 없음(로그 호출은 memberId·stage·reason·예외 클래스, 실제 로그 일치 2줄은 `keyvalue` 클래스 이름, 비밀은 Base64·hex·base64url). 지시대로 **미적용** |
| 검증 | `git diff --check` | 오류 없음 |
