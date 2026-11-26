# frontend — test-spec

요구사항·합격 기준(`FE-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

"테스트" 칸은 `frontend/` 기준 테스트 파일이다.

| ID | 요구사항 | 합격 기준 | 근거 | 테스트 |
| --- | --- | --- | --- | --- |
| FE-T001 | 재현 가능한 설치와 정적 검사·빌드 | `npm ci` 뒤 lint, typecheck(`tsc -b --noEmit`, 앱·테스트·설정 tsconfig 전체), test, build가 모두 exit 0 | FE-D003, FE-D008 | `npm ci`·`npm run lint`·`npm run typecheck`·`npm test`·`npm run build` (명령 검증) |
| FE-T002 | 오류 응답 정규화 | ProblemDetail의 `code`·`errors[]`를 정규화한다. ProblemDetail이 아니거나 JSON이 아닌 응답도 status를 보존하고 없는 `code`를 만들지 않는다. 2xx 형식 오류를 구분한다 | FE-D007, G-D003 | `apiClient.test.ts`(HTTP 오류 정규화, parse 실패·비동기 parse), `problemDetail.test.ts`(Content-Type 판별) |
| FE-T003 | 전송 오류 분류와 Timeout | network, timeout, canceled를 구분한다. 기본 Timeout 10초, 요청별 재정의, 잘못된 값(0·음수·NaN·Infinity 등)은 거부 | FE-D007 | `apiClient.test.ts`(전송 오류 분류), `requestOptions.test.ts`(resolveTimeoutMs) |
| FE-T004 | XSRF 자동 처리 비활성 | jsdom에서 XSRF 쿠키가 있어도 `X-XSRF-TOKEN` 헤더가 붙지 않는다(XHR 어댑터) + 설정값 단언. 기본 Axios는 헤더를 붙인다는 대조군을 둔다 | FE-D014 | `apiClient.test.ts`(XSRF 자동 처리 비활성: 대조군·헤더 없음·보안 기본값) |
| FE-T005 | 요청 URL 검증 | 절대 URL, `//` URL, 다른 출처, `..`·퍼센트 인코딩·역슬래시로 `/api/` 밖을 가리키는 경로는 요청 전에 거부한다. 요청별 baseURL·보안 옵션을 바꿀 수 없다 | FE-D013 | `urlGuard.test.ts`, `apiClient.test.ts`(요청 전 거부) |
| FE-T006 | 인증 영향 범위 필수 지정 | 범위를 빠뜨리면 타입 오류다(`@ts-expect-error`로 확인) | FE-D010 | `requestOptions.test.ts`(ApiRequest 타입, `tsc -b`가 검사) |
| FE-T007 | 401 판정 | 범위 × `code` × 현재 상태 × 세대 조합의 판정이 기대와 같다. 알 수 없는 `code`는 인증 상태를 바꾸지 않는다 | FE-D010 | `apiClient.session.test.ts`(401 판정), `sessionScope.test.ts`, `classifyInitFailure.test.ts` |
| FE-T008 | 동시 401 중복 방지 | 동시에 401이 N건 와도 상태 전이·알림·이동이 각각 1회 | FE-D010, FE-D011 | `AuthProvider.test.tsx`(동시 401 5건), `sessionScope.test.ts`, `authState.test.ts`(세션 상실), `RequireAuth.test.tsx`(이동 1회) |
| FE-T009 | 오래된 응답 무시 | 오래된 세대의 응답은 반영하지 않는다. 초기화 재시도 때 최신 요청의 응답만 반영한다 | FE-D011 | `apiClient.session.test.ts`(오래된 세대 응답), `authState.test.ts`(오래된 초기화 결과), `AuthProvider.test.tsx`(StrictMode, 재시도·재초기화), `apiClient.session.test.ts`(비동기 parse 중 세대 전환) |
| FE-T010 | 초기화 결과 분류 | 초기화 401 → 미인증. 503·5xx·network·timeout → initError(이동 없음). 재시도하면 initError를 정리한다. 초기화 전체에 상한이 있어 무한 로딩이 없다 | FE-D009 | `AuthProvider.test.tsx`(초기화·Deadline, 재시도·재초기화), `classifyInitFailure.test.ts`, `authState.test.ts`(초기화 전이) |
| FE-T011 | 선택적 취소 | 세대·범위에 따라 이전 인증 의존 요청만 취소한다. 호출자 signal과 결합한다. 취소는 화면 오류로 표시하지 않는다 | FE-D011 | `apiClient.session.test.ts`(선택적 취소), `signals.test.ts`, `AuthProvider.test.tsx`(세션 상실 시 취소) |
| FE-T012 | Auth Guard | 4가지 분기(로딩, initError 재시도, 미인증 → 로그인 이동, 인증 → 화면). 안전한 내부 경로만 `from`으로 보존하고 그 밖은 `/` | FE-D012 | `RequireAuth.test.tsx`, `safeRedirect.test.ts`, `router.test.tsx`(앱 라우팅) |
| FE-T013 | errorElement 역할 | errorElement는 라우팅 오류만 처리한다. API 오류는 그쪽으로 가지 않는다 | FE-D012 | `router.test.tsx`(RouteError 역할) |
| FE-T014 | 등록 1회 | StrictMode에서도 Interceptor와 인증 상태 연결이 한 번만 등록된다 | FE-D010 | `apiClient.session.test.ts`(브리지 1개), `sessionScope.test.ts`(리스너 1개), `AuthProvider.test.tsx`(StrictMode 동시 401·언마운트 해제) |
| FE-T015 | 운영 어댑터 안전성 | 운영 어댑터는 네트워크 호출 없이 항상 미인증이고 인증 성공 경로가 없다. 빌드 결과에 가안 표지 문자열과 `src/test` 모듈이 없다 | FE-D005 | `AuthProvider.test.tsx`(운영 어댑터), `forbidTestModules.test.ts`, `checkDist.test.ts`, `npm run build`의 checkDist |
| FE-T016 | 의존 방향 lint | 의존 방향 규칙(`axios` import 위치, `src/test` import 금지 등)이 위반 예시에서 실패한다 | FE-D005, FE-D013 | `eslintBoundaries.test.ts` |
| FE-T017 | Reducer 순수성 | 같은 입력에 같은 출력. 부수 효과 Mock이 호출되지 않는다 | FE-D009 | `authState.test.ts`(순수성) |
| M-01 | XSRF 실제 브라우저 수동 확인 | 개발 서버에서 `XSRF-TOKEN` 쿠키를 둔 채 Client 요청을 보내 Network 탭에 `X-XSRF-TOKEN` 헤더가 없음을 확인한다. 자동 결과와 구분해 기록하고, 하지 않았으면 PASS로 적지 않는다. 운영 빌드에서는 확인할 수 없다는 한계를 함께 적는다 | FE-D014 | 수동 |

## 2. 집계

- 요구사항 17건(FE-T001~FE-T017) + 수동 1건(M-01). Vitest 17파일 218건(shared/api 85, features/auth 86, app 8, src/test 25, scripts 14).
  FE-T001은 명령 실행으로 검증한다. 실행 결과는 [regression-log.md](regression-log.md).
