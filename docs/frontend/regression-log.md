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
