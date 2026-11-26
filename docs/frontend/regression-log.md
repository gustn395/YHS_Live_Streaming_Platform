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

