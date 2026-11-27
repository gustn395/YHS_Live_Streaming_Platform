# frontend — test-spec

요구사항·합격 기준(`FE-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

"테스트" 칸은 `frontend/` 기준 테스트 파일이다.

| ID | 요구사항 | 합격 기준 | 근거 | 테스트 |
| --- | --- | --- | --- | --- |
| FE-T001 | 재현 가능한 설치와 정적 검사·빌드 | `npm ci` 뒤 lint, typecheck(`tsc -b --noEmit`, 앱·테스트·설정 tsconfig 전체), test, build가 모두 exit 0 | FE-D003, FE-D008 | `npm ci`·`npm run lint`·`npm run typecheck`·`npm test`·`npm run build` (명령 검증) |
| FE-T002 | 오류 응답 정규화 | ProblemDetail의 `code`·`errors[]`를 정규화한다. ProblemDetail이 아니거나 JSON이 아닌 응답도 status를 보존하고 없는 `code`를 만들지 않는다. 2xx 형식 오류를 구분한다 | FE-D007, G-D003 | `apiClient.test.ts`(HTTP 오류 정규화, parse 실패·비동기 parse), `problemDetail.test.ts`(Content-Type 판별) |
| FE-T003 | 전송 오류 분류와 Timeout | network, timeout, canceled를 구분한다. 기본 Timeout 10초, 요청별 재정의, 잘못된 값(0·음수·NaN·Infinity 등)은 거부 | FE-D007 | `apiClient.test.ts`(전송 오류 분류), `requestOptions.test.ts`(resolveTimeoutMs) |
| FE-T004 | XSRF 자동 처리 비활성 | jsdom에서 XSRF 쿠키가 있어도 GET 요청에 `X-XSRF-TOKEN` 헤더가 붙지 않는다(XHR 어댑터) + 설정값 단언. 기본 Axios는 헤더를 붙인다는 대조군을 둔다. 상태 변경 요청의 직접 부착은 FE-T019 | FE-D014, FE-D020 | `apiClient.test.ts`(XSRF 자동 처리 비활성: 대조군·헤더 없음·보안 기본값) |
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
| FE-T018 | Frontend CI | Frontend 변경이 있으면 `frontend-ci` Workflow가 `npm ci`·lint·typecheck·test·build와 FE-D017 audit을 실행하고, 하나라도 실패하면 Gate `frontend-ci`가 실패한다. audit 실행 오류는 `AUDIT_ERROR`로 실패한다. Backend 서버 없이 통과한다. Artifact를 올리지 않는다. 공통 감지·Gate 시나리오는 [fe02-ci-split.md](../testing/fe02-ci-split.md)가 원본이다 | FE-D017, FE-D018, FE-D019, FE-T001 | `.github/workflows/frontend-ci.yml`, `.github/ci/tests/test_npm_audit_report.py`(저장소 루트 기준), GitHub Actions 실행(fe02-ci-split.md 4장 L3·L4) |
| FE-T019 | CSRF 부착 범위 | POST·PUT·PATCH·DELETE에만 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN`으로 붙이고 GET·HEAD·OPTIONS에는 붙이지 않는다(`/csrf`도 부르지 않음). 최종 URL이 같은 출처의 `/api/` 아래가 아니면 부착 대상이 아니다(다른 출처·`//`·포트 다름·`/apix`·`/api` 자체 거부). 요청 옵션으로 헤더를 넣을 수 없다(FE-T005·FE-T006 유지) | FE-D020, FE-D031 | `apiClient.csrf.test.ts`(CSRF 헤더 부착 범위) |
| FE-T020 | CSRF 토큰 확보 | 쿠키가 있으면 `/csrf`를 부르지 않는다. 없으면 동시 3건에 `/csrf` 1회. 발급 실패 시 기다리던 요청이 모두 실패하고 전송 0건, 다음 시도에서 다시 부른다. 204인데 쿠키가 없으면 `csrf-unavailable`이고 원래 요청을 보내지 않는다 | FE-D020, FE-D031 | `apiClient.csrf.test.ts`(CSRF 토큰 확보) |
| FE-T021 | CSRF 대기 중 취소·세대 | 기다리던 요청 하나를 취소하면 그 요청만 `canceled`·미전송이고 나머지는 보낸다(`/csrf`는 1회, 중단되지 않음). 기다리는 동안 세대가 바뀌면 session 요청을 보내지 않는다. 기존 세대·취소 테스트(FE-T009·FE-T011)가 그대로 통과한다 | FE-D011, FE-D031 | `apiClient.csrf.test.ts`(CSRF 대기 중 취소), `apiClient.session.test.ts` |
| FE-T022 | CSRF 403 | 403 `AUTH_CSRF_INVALID`는 `csrf-retry`로 분류하고 그 밖의 403은 `forbidden`이다. 원래 요청을 다시 보내지 않고(1회), 다음 상태 변경 요청 전에 쿠키가 있어도 `/csrf`를 부르고 새 값을 붙이며, 성공한 뒤에는 다시 강제하지 않는다. 경쟁: 일반 발급 중 403 뒤의 요청은 그 발급 뒤 새 발급을 한 번 더 거치고, 강제 발급 중 동시 요청은 합류하며, 강제 발급 중 새 403 뒤의 요청은 한 번 더 발급하고, 강제 발급이 실패하면 복구 요구가 남는다. 403을 받은 토큰이 이미 바뀌었으면 강제하지 않고, 세대가 바뀐 응답이어도 보낸 토큰이 그대로면 복구 요구를 남긴다(응답은 `canceled`). 대기 중 취소는 그 요청만 빠진다. `csrf-unavailable`은 공통 오류, 초기화에서는 미인증이 아닌 initError | FE-D021, FE-D031 | `apiClient.csrf.test.ts`(CSRF 403 이후), `csrfToken.test.ts`, `errorPresentation.test.ts`, `classifyInitFailure.test.ts` |
| FE-T023 | Retry-After 파싱 | 1 이상의 안전한 정수 초만 `retryAfterSeconds`로 둔다. 누락·0·음수·소수·문자·HTTP-date·안전 정수 초과는 없음 | FE-D025, FE-D031 | `apiClient.csrf.test.ts`(Retry-After) |
| FE-T024 | 가입 입력 검증 | username(ASCII만 소문자 변환, U+212A 거부, 4~20자), 비밀번호(NFC code point 10~30([MB-D026](../member/decisions.md#1-확정-결정)), 9·10·30·31자 경계, 이모지·NFD, 공백 유지, 오류 문구가 Backend와 같음, 비밀번호·username이 모두 ASCII일 때 Backend 문맥 단어 규칙 전체 재현, 비ASCII는 서버 판정, 길이 위반 우선), 비밀번호 확인(원문 비교), 닉네임(Java `strip` 기준 공백, Cc·Cf 거부, code point 2~20), 이메일(마지막 `@` 앞뒤 비어 있지 않음, 도메인의 공백·`@` 거부, local part를 따옴표 밖의 점으로 나눈 조각마다 따옴표 조각은 안쪽 공백·`@`·백슬래시 이스케이프(`\"`·`\\`) 허용(빈 따옴표·이어 붙은 따옴표·닫히지 않은 따옴표 거부), 일반 조각은 공백·`@`·따옴표 거부 — Hibernate Validator 9.1.x 실행 결과와 일치, 255자) | FE-D022, FE-D031, FE-D033, MB-D004·D005·D008·D026 | `signupValidation.test.ts` |
| FE-T025 | 가입 요청·응답 계약 | 본문 `username·password·passwordConfirm·email·nickname`, 비밀번호 원문 유지, username 소문자, CSRF 헤더 포함. 상태 코드 201이고 `memberId` 양의 안전 정수·`accountStatus=PENDING`일 때만 성공이고 그 밖(201이 아닌 2xx(200·202·204), 누락·0·음수·소수·문자열·범위 초과·PENDING 아님·본문 없음)은 `invalid-response`·재전송 없음. 공통 Client는 parse에 응답 상태 코드를 넘긴다. network도 재전송 없음 | FE-D031, FE-D032 | `signupApi.test.ts` |
| FE-T026 | 가입 화면 오류·상태 | 입력 중 오류 없음 → blur 검사 → 오류(클라이언트·서버)가 표시된 필드는 blur 없이 입력마다 재검사. 제출 시 전체 검사·오류 요약 alert·첫 오류 포커스·전송 0건. 400 `errors[]` 필드 표시, 유출 비밀번호 고정 문구(서버 detail 미표시), 409 필드 표시·수정 시 해제·입력 유지(409가 아닌 응답의 중복 코드, 400이 아닌 응답의 유출 코드는 해당 안내 없이 공통 배너), CSRF가 아닌 403은 화면 이동 없이 공통 배너·입력 유지, 503 두 코드 재시도 안내, CSRF 403 안내·입력 유지·재전송 없음, `csrf-unavailable` 안내, network·비정상 201·201이 아닌 2xx "이미 가입됐을 수 있음" 안내, 처리 중 submit 이벤트가 몇 번 와도 요청 1건(버튼 상태와 무관), 429 `Retry-After` 동안 버튼 비활성·submit 이벤트도 차단 → 마감 시각이 지나면 활성(자동 제출 없음, 2^31-1ms보다 긴 대기도 마감 전 해제 없음), `Retry-After` 600·601·3600초는 약 10·11·60분 안내와 마감 전 차단(제한 수치는 코드에 두지 않음), `Retry-After` 없으면 시간 없이 안내, 언마운트 때 요청 취소·대기 타이머 정리. 늦은 서버 필드 오류는 제출 뒤 판정 값이 바뀐 필드에 붙이지 않는다(아이디·이메일은 정규화 값 기준, 비밀번호 오류는 아이디에도 의존, 유출 비밀번호는 비밀번호만). 붙일 오류가 하나도 없으면 필드 오류·포커스를 그대로 두고 고정 안내를 `role="status"`로 보인다(부재 단언은 이 안내가 보인 뒤에 한다). 서버 오류가 오면 포커스는 표시 중인 클라이언트 오류와 합친 목록의 첫 필드로 간다. 표시된 서버 오류는 의존 입력이 바뀌면 지운다(아이디 변경은 비밀번호 `errors[]` 오류를 지우고 유출 오류는 유지, 비밀번호 변경은 확인 서버 오류도 지움). 오류 요약은 서버 필드 오류에도 보이고, 개수 없는 고정 문구라 입력 중 바뀌지 않으며, 오류가 모두 사라지면(입력·blur·재제출 어느 경로든) 꺼져 제출 없이 blur로 오류가 생겨도 다시 알리지 않고, 다시 실패하면 새 요소로 알린다. 낡은 결과 안내는 다음 제출 때 지워진다. 렌더 사이 없이 이어진 입력이 값·서버 오류를 서로 덮지 않는다 | FE-D021~FE-D025, FE-D028, FE-D031~FE-D033 | `SignupForm.test.tsx`(검사 시점, 서버 응답, 늦은 서버 응답과 바뀐 입력, 오류 요약 알림, 제출 가드와 정리, Retry-After 대기, CodeRabbit 반영). FE-04에서 429 대기를 공용 `shared/time/deadline.ts`로 바꾼 뒤 이 파일을 고치지 않고 통과(FE-D040 (3)) |
| FE-T027 | 가입 완료 흐름·인증 상태 | 201 → `/signup/complete` replace, memberId 미표시, 로그인 링크. 뒤로 가기는 가입 폼이 아니라 그 전 화면. state가 있으면 새로고침해도 표시, 없으면 `/signup`으로 replace. 인증된 상태에서 가입해도 인증 상태·주체가 그대로 | FE-D026, FE-D009 | `SignupPage.test.tsx`(가입 완료 흐름, 인증 상태) |
| FE-T028 | 가입 화면 접근성·비밀번호 필드 | `aria-invalid`·`aria-describedby`로 오류 연결, `autocomplete`(username·email·new-password), 비밀번호 토글 2개 독립(`aria-pressed`·type, 보이는 글자는 상태와 무관하게 "표시"이고 이름은 "비밀번호 표시"·"비밀번호 확인 표시"), 붙여넣기 허용, 길이 안내("10~30자, 공백도 쓸 수 있습니다")는 비밀번호 필드에 상시, username 소문자 변환 안내와 입력값 유지 | FE-D023, FE-D028, FE-D031~FE-D033 | `SignupForm.test.tsx`(접근성) |
| FE-T029 | 가입 경로·링크 | `/signup`은 운영 어댑터(미인증)에서도 공개 경로로 보인다. 홈·로그인 화면에서 가입으로, 가입 화면에서 로그인으로 가는 링크 | FE-D012, FE-U10 | `SignupPage.test.tsx`(가입 경로) |
| FE-T030 | 로그인 요청·응답 계약 | 본문 `username·password`를 입력 그대로(대문자·31자 이상·앞뒤 공백 유지, 소문자 변환·Trim·길이 검사 없음), CSRF 헤더 포함. 상태 200이고 `status=OTP_REQUIRED`·`otpExpiresAt` ISO 시각(형식 규칙은 FE-T031과 같음, 재발송과 같은 파서)일 때만 성공이고 그 밖(201·202·204, 필드 누락·형식 오류·숫자 시각·본문 없음)은 `invalid-response`·재전송 없음. 401은 http 실패 그대로 | FE-D037, FE-D032, FE-D033 (3), FE-D043 | `loginApi.test.ts` |
| FE-T031 | OTP 확인·재발송 계약 | 확인 본문은 `{"code":"0420"}`(문자열, 앞자리 0 유지). 200이고 `memberId` 양의 안전한 정수·`role` 비지 않은 문자열·`accessTokenExpiresAt` ISO 시각일 때만 성공. 시각은 Backend Instant 형식(ISO-8601 UTC `Z`, 소수 초 0~9자리)만 받고 RFC 1123·시간대 없음·날짜만·오프셋 표기·소수 초 10자리는 거부(FE-D043). 형식이 맞아도 없는 날짜·시각(0월·13월·0일·32일·2월 30일·4월 31일·평년 2월 29일(1900·2023)·24시·60분·60초)은 거부하고, 윤일과 소수 초 9자리는 허용. 해석은 엔진의 `Date.parse`에 기대지 않는다(`Date.parse`를 NaN으로 바꿔도 같은 값): 소수 초는 반올림하지 않고 밀리초로 잘라내며(`…59.9999999Z` → 같은 초 999ms), 0~99년과 0년도 그대로 해석한다(1900년대로 바꾸지 않음)(FE-D043 (1) 세부, CodeRabbit 2차). 재발송은 본문 없이 보내고 200 OTP 대기 응답만 성공. 그 밖은 `invalid-response`·재전송 없음 | FE-D034, FE-D035, FE-D043 | `otpApi.test.ts` |
| FE-T032 | 로그인 성공 전환 | Reducer `LOGIN_SUCCEEDED`: `unauthenticated`(최초·세션 상실 뒤)·`authenticated`에서 세대 +1·주체 교체, `initializing`·다른 세대는 같은 참조. Provider `completeLogin`: 초기화 결과 전이면 false·반영 없음, 성공하면 세대 관리(sessionScope)와 같은 세대로 전이, 로그인 전 세대 session 요청 취소, 로그인 뒤 401은 같은 세대로 세션 상실 반영 | FE-D034, FE-D011, FE-D016 | `authState.test.ts`(LOGIN_SUCCEEDED), `AuthProvider.test.tsx`(로그인 성공 전환) |
| FE-T033 | 로그인 폼 | 빈 값이면 요청 0건·안내·빈 첫 필드 포커스. 형식·길이 검사 없음. autocomplete `username`·`current-password`, 비밀번호 가림. 401·400은 같은 문구, 403 `MEMBER_ACCOUNT_DISABLED` 안내, CSRF 403 안내, 그 밖의 403·500·형식 오류는 공통 문구로 화면 유지(FE-D040 (2)), 503 재시도 안내, 모든 실패에서 입력 유지. 429 `Retry-After` 동안 버튼·submit 차단 → 지나면 활성(자동 제출 없음), 없으면 시간 없이 안내. 처리 중 submit 여러 번에도 요청 1건, 초기화 중(ready=false) 제출 없음, 다시 제출하면 이전 안내 지움, 화면을 떠나면 결과 반영 없음 | FE-D037, FE-D025, FE-D021, FE-D040 | `LoginForm.test.tsx` |
| FE-T034 | 로그인 → OTP 이동 | 성공하면 `/login/otp`로 `otpExpiresAt`·재발송 가능 시각·`from`을 history state로 넘긴다. `from`은 RequireAuth가 붙인 `?from=`을 safeRedirect로 검사한 값이고 안전하지 않으면 FE-D012대로 `/` | FE-D036, FE-D012 | `LoginPage.test.tsx`(일반 로그인 흐름) |
| FE-T035 | OTP 입력 | 입력 칸 하나, `inputmode=numeric`·`autocomplete=one-time-code`. 숫자가 아닌 문자를 거른 뒤 4자리로 자른다(붙여넣기 `"0a4 2-0 9"` → `"0420"`, `maxLength`를 두지 않아 원문이 먼저 잘리지 않음). 4자리 전에는 확인 비활성, 자동 제출 없음 | FE-D035 | `OtpForm.test.tsx`(OTP 입력) |
| FE-T036 | OTP 확인 결과 | 성공하면 `completeLogin` 뒤 `from`(없으면 `/app`)으로 replace 이동, 뒤로 가기로 OTP 화면에 돌아가지 않음. `AUTH_OTP_MISMATCH`는 입력 지움·포커스·횟수 미표시, `AUTH_OTP_EXPIRED`는 재발송 안내, `AUTH_OTP_CHALLENGE_INVALID`·OTP 단계 `AUTH_LOGIN_FAILED`·`MEMBER_ACCOUNT_DISABLED`는 `/login`(`from` 유지)으로 replace하고 사유 안내·인증 상태 그대로. CSRF 403·그 밖의 403·503은 화면 유지·입력 유지. 429는 `Retry-After` 동안 확인 버튼 차단. history state가 이상하면(숫자 아닌 시각, 조작된 `from`) 쓰지 않거나 다시 검사. 확인은 성공했는데 `completeLogin`이 false면 이동하지 않고 반영 실패 안내(OTP 화면에는 처음부터 다시 로그인 링크가 늘 있고 `from`을 유지). 다시 시작 사유는 한 번 보인 뒤 history state에서 지움(`from` 쿼리 유지). 폼 공통 실패 분류(취소·CSRF·전송·429·503·그 밖) | FE-D037, FE-D034, FE-D036, FE-D040, FE-D042 | `OtpForm.test.tsx`(OTP 확인 실패), `LoginPage.test.tsx`(일반 로그인 흐름, Google 진입), `OtpPage.test.tsx`, `formFailure.test.ts` |
| FE-T037 | 재발송·남은 시간·동시성 | 재발송 가능 시각 전에는 버튼 비활성과 남은 초(1초마다 갱신), 지나면 활성. 발급 시각을 모르는 진입(Google·state 없음)은 바로 활성이고 남은 시간 미표시. OTP 남은 시간 분:초(125초 → 2:05), 지나면 만료 안내. 재발송 성공 → 새 만료 시각·60초 대기·입력 비움·화면 기록(history state) 갱신, Challenge 없음 → 다시 시작, 429는 재발송만 차단. 확인과 재발송은 동시에 나가지 않음. 화면을 떠나면 타이머 0개·진행 중 결과 미반영. 확인 429 동안 재발송은 그대로 가능. 공용 대기 모듈: `Retry-After` 초 → 분 올림, 마감 시각에 한 번만 알림, 지난 시각은 즉시, 2^31-1ms를 넘는 대기 분할, 취소, tick 간격 | FE-D035, FE-D040 (3) | `OtpForm.test.tsx`(재발송과 남은 시간), `deadline.test.ts`, `LoginPage.test.tsx`(재발송 화면 기록) |
| FE-T038 | Google 시작·오류 안내 | 로그인 화면에 `/api/v1/auth/oauth2/authorization/google`로 가는 링크(문서 이동). `?error=` 6개 코드는 서로 다른 고정 문구, 알 수 없는 값(빈 값·대문자·HTML·뒤 공백 포함)은 일반 문구이며 받은 값을 화면에 그리지 않는다. `rate_limited`는 대기 시간을 말하지 않는다. 표시 뒤 쿼리에서 `error`만 replace로 지우고(`from` 유지) 문구는 남긴다 | FE-D038 | `googleLoginError.test.ts`, `LoginPage.test.tsx`(Google 오류 안내) |
| FE-T039 | 로그인·OTP 경로 | `/login`·`/login/otp`는 공개 경로이고 준비 중 Placeholder가 없다. 가드 → `/login?from=` → 로그인 → OTP → 원래 화면, 인증 상태 `authenticated`. 로그인 화면에 가입 링크, 세션 상실 안내 유지 | FE-D012, FE-D036 | `LoginPage.test.tsx`, `router.test.tsx`, `SignupPage.test.tsx`(가입 경로) |
| FE-T040 | HIBP 스텁 계약(CI E2E) | Backend HibpPwnedPasswordsClient와 같은 `GET /range/{대문자 16진수 5자}` + `Add-Padding: true` + `User-Agent: yhs-live-streaming-backend`에만 200과 건수 0인 패딩 줄(Backend 정규식 `[0-9A-Fa-f]{35}:\d+`에 맞음, "유출 없음")을 준다. 소문자·길이·16진수 아님·쿼리·헤더 누락·다른 값·GET 아님은 400, 다른 경로는 404이고 unexpected로 센다. `/healthz`는 200이고 정상 호출로 세지 않는다. 통계(`ok`·`unexpected`·`health`)는 기동 때 0으로 초기화하고 요청마다 원자적으로 다시 써서 서버가 떠 있는 동안에도 최신이다. 127.0.0.1에만 바인드하고 포트가 쓰이고 있으면 실패한다. 실제 HIBP로 대신 요청하지 않는다 | FE-D044 (4), fe06 Q5 | `e2e/support/hibpStub.test.ts` |
| FE-T041 | E2E CI | E2E 대상 변경(fe06 3장)이 있으면 `e2e-ci` Workflow가 서버를 띄워 PR은 `@slow`를 뺀 E2E(01·02), main push·수동 실행은 전체(01~03)를 실행한다. 실행 전 목록으로 필수 집합을 만들고 0건·skipped·flaky·unexpected·필수 누락·범위 밖 실행이면 실패, 스텁 정상 호출이 실행 수보다 적거나 예상 밖 요청이 있으면 실패, 하나라도 실패하면 Gate `e2e-ci`가 FAILED다. 공개 로그에는 결과·집계 줄과 거른 제한 로그만 남고 Artifact가 없다. 실패하면 Step Summary에 첫 실패 단계(고정 이름)를 남긴다. GitHub 실행 검증은 fe06 Q15 범위(L3 구현 PR, L4 main push)이고 나머지는 FE-U12 | FE-D044, fe06 Q1~Q13·Q15 | `.github/ci/tests/`(감지·Gate·결과 검증·로그 필터·스텁 검증·실패 단계), `.github/workflows/e2e-ci.yml` |
| M-02 | 회원가입 실제 Backend 수동 확인 | 개발 서버 + 실제 Backend로 Chrome에서 확인한다: 가입 성공과 완료 화면, `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더, CSRF 403 복구(Console에서 `path=/signup`인 가짜 `XSRF-TOKEN` 쿠키를 더해 403 유도 → 그 쿠키만 지움 → 다시 제출해 `/csrf` 재확보 뒤 성공. DevTools Override headers는 요청 헤더를 바꾸지 못해 쓰지 않는다), 400·409 필드 표시, 폭 320px에서 가로 스크롤 없음, 글자 대비, 다크 모드. Safari는 확인하지 않았으면 미확인으로 적는다. 실행은 별도 승인 | FE-D029 | 수동 |
| M-01 | XSRF 실제 브라우저 수동 확인 | 개발 서버에서 `XSRF-TOKEN` 쿠키를 둔 채 Client GET 요청을 보내 Network 탭에 `X-XSRF-TOKEN` 헤더가 없음을 확인한다(상태 변경 요청의 부착은 M-02). 자동 결과와 구분해 기록하고, 하지 않았으면 PASS로 적지 않는다. 운영 빌드에서는 확인할 수 없다는 한계를 함께 적는다 | FE-D014 | 수동 |
| E2E-01 | 일반 로그인 성공(`@smoke`) | 새 계정 → `/app` 접근 → `/login?from=%2Fapp` → 로그인 → Mailpit OTP → 확인: `/app` 도착, OTP 확인 요청에 `X-XSRF-TOKEN`, `__Host-yhs_at` 쿠키 HttpOnly·Secure·SameSite=Strict·Path=/ | FE-D034~FE-D037, FE-D039, FE-D044 | `e2e/login.spec.ts` (E2E, 아래 3장 절차, CI는 PR·main push·수동) |
| E2E-02 | OTP 오입력 뒤 성공 | 정답과 다른 4자리 → 일치하지 않음 안내·입력 비움 → 정답으로 `/app` | FE-D037, FE-D039, FE-D044 | `e2e/login.spec.ts` (E2E, CI는 PR·main push·수동) |
| E2E-03 | 재발송·이전 OTP 무효(`@slow`) | 첫 OTP 수신 → 재발송 버튼이 켜질 때까지 대기(약 60초) → 재발송 → 새 메일만 읽음 → 두 번호가 다름 → 이전 번호는 일치하지 않음 안내 → 새 번호로 `/app` | FE-D035, FE-D039, FE-D044, MB-D015 | `e2e/login.spec.ts` (E2E, CI는 main push·수동만) |
| E2E-ENV | E2E 환경 격리·반복 | 3장 절차를 `down -v`를 사이에 두고 2회 실행해 2회 모두 통과하고, 끝난 뒤 `yhs-e2e` 프로젝트 컨테이너·볼륨이 0개. 환경변수가 없거나 서버가 없으면 globalSetup이 이유를 밝히고 멈춘다 | FE-D039 | 절차 확인(E2E) |
| M-03 | 로그인·OTP 실제 브라우저 수동 확인 | Chrome: 실제 Google 로그인(실제 Google OAuth 클라이언트가 필요해 사용자가 판단, 없으면 미실행) → OTP 화면 진입·성공, Backend 302 `/login?error=` 실제 안내, 폭 320px 가로 스크롤 없음·다크 모드·대비, 인증 쿠키 속성. Safari는 확인하지 않았으면 미확인으로 적는다. 실행은 별도 승인 | FE-D029, FE-D038 | 수동 |

## 2. 집계

- 요구사항 41건(FE-T001~FE-T041, FE-T018·FE-T041의 GitHub 실행 검증은 PR 이후) + E2E 4건(E2E-01~03, E2E-ENV) + 수동 3건(M-01~M-03).
  Vitest 33파일 569건(shared/api 135, shared/time 8, features/auth 97, features/login 44, features/otp 64, features/signup 133, app 32,
  src/test 25, scripts 14, e2e/support 17). E2E는 Playwright 3건이고 CI 실행 범위는 [FE-D044](decisions.md#1-확정-결정) (2)다.
  CI 스크립트 unittest(`.github/ci/tests/`)는 [fe06-e2e-ci.md](../testing/fe06-e2e-ci.md) 4장 L1·L2다.
  FE-T001은 명령 실행으로 검증한다. 실행 결과는 [regression-log.md](regression-log.md).

## 3. E2E 실행 절차 (FE-D039, FE-D040)

로컬 절차다(CI 절차는 [fe06-e2e-ci.md](../testing/fe06-e2e-ci.md) 2장). Windows PowerShell 기준이며, 저장소 루트에서 실행한다. 비밀 값은
어디에도 적지 않는다(이 문서·regression-log 포함). OTP·재발송 관련 코드를 바꾼 작업은 Merge 전에 Full을 한 번 실행하고 결과를
regression-log에 적는다(CI 수동 실행 결과도 인정, [FE-D044](decisions.md#1-확정-결정) (3)).

1. 준비: Docker, Java 17(Backend toolchain), Node 24, 인터넷(가입 때 Backend가 HIBP를 호출한다. 기본은 실제 HIBP이고, 선택으로 8번의 스텁을 쓸 수 있다). 브라우저는 한 번만
   `npm --prefix frontend exec playwright install chromium`(저장소 밖 사용자 캐시).
2. 환경변수(새 PowerShell 창, 실행할 때마다 새로 만든다): `E2E_POSTGRES_PASSWORD`와 Backend 키 3종(`AUTH_JWT_KEY`, `MEMBER_OTP_HMAC_KEY`,
   `MEMBER_PASSWORD_PEPPER_KEY`, 서로 다른 값)은 아래처럼 256bit 난수를 Base64로 만든다. 키 ID(`*_KEY_ID`)는 아무 식별자(예: `e2e`).
   `$b=[byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`
   Backend 변수 이름과 형식의 원본은 [backend/README.md 4장](../../backend/README.md#4-설정과-환경변수)이다. E2E 값: DB
   `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:15432/yhs`, 사용자 `yhs`, 비밀번호 `E2E_POSTGRES_PASSWORD`와 같은 값, Redis `127.0.0.1:16379`,
   메일 `127.0.0.1:11025`(`MEMBER_SMTP_AUTH=false`, `MEMBER_SMTP_STARTTLS=false`), `BACKEND_PORT=18080`, Google은 시작하지 않으므로 ID·Secret에
   더미 값, `MEMBER_OAUTH_PUBLIC_BASE_URL=http://localhost:5173`.
3. 시작 전 정리: `docker compose -p yhs-e2e -f infrastructure/docker/e2e/compose.yml down -v`. 이전 실행의 DB·Redis 익명 볼륨과 Mailpit 메일을
   지운다. 다른 Docker 프로젝트·볼륨은 건드리지 않으며 `docker system prune`은 쓰지 않는다.
4. 기동: `docker compose -p yhs-e2e -f infrastructure/docker/e2e/compose.yml up -d --wait`(세 서비스 healthy) → Backend
   `.\backend\gradlew.bat -p backend :app:bootRun --no-daemon`(빈 DB에 Flyway 적용) → Frontend
   `$env:FRONTEND_API_PROXY_TARGET='http://127.0.0.1:18080'; npm --prefix frontend run dev`. DB를 다시 초기화하면 Backend도 다시 기동한다.
5. 실행: `$env:E2E_BASE_URL='http://localhost:5173'; $env:E2E_MAILPIT_URL='http://127.0.0.1:18025'`, 그다음 범위별로
   - Smoke: `npm --prefix frontend run e2e:smoke`(E2E-01)
   - 기본: `npm --prefix frontend run e2e -- --grep "E2E-0[12]"`(E2E-01·02. CI의 PR 범위 `-- --grep-invert @slow`와 같은 테스트)
   - Full: `npm --prefix frontend run e2e`(E2E-01~03, 재발송 대기로 약 2분). **Commit 전 필수 범위는 Full 1회**다.
   workers 1·재시도 없음이고, 테스트마다 새 계정을 만들어 회원별 OTP 제한과 Mailpit 수신함이 섞이지 않는다. 한 실행에서 쓰는 요청은 가입 3·로그인 3·
   OTP 확인·재발송 7회 안팎으로 IP 제한(수치 원본: member [MB-D018](../member/decisions.md#1-확정-결정)·[MB-D027](../member/decisions.md#1-확정-결정)) 안이다. 같은 환경에서 다시 돌릴 때는 3번부터 한다.
6. 실패 조사: 환경을 끄기 **전에** 확인한다. 실패한 테스트의 trace·스크린샷은 OS 임시 폴더 `yhs-e2e/test-results`에만 남는다
   (`npm --prefix frontend exec playwright show-trace <trace.zip>`). trace에는 테스트 비밀번호·OTP가 들어가므로 저장소·Issue·PR에 올리지 않는다.
   HTML 리포트는 만들지 않는다.
7. 종료: Frontend·Backend를 끄고 3번과 같은 `down -v`. `docker ps -a --filter label=com.docker.compose.project=yhs-e2e`와
   `docker volume ls --filter label=com.docker.compose.project=yhs-e2e`가 비었는지 확인한다(E2E-ENV).
8. 선택: HIBP 스텁(FE-T040). 실제 HIBP 대신 CI와 같은 스텁으로 돌릴 때만 쓴다. 이 실행은 실제 HIBP 연동 확인을 대신하지 않는다.
   4번 Backend 기동 **전에** 새 창에서 `$env:HIBP_STUB_PORT='18099'; $env:HIBP_STUB_STATS_FILE="$env:TEMP\yhs-e2e-hibp-stats.json";
   node frontend/e2e/support/hibpStub.ts`를 띄우고, Backend 창에 `$env:MEMBER_PASSWORD_HIBP_BASE_URL='http://127.0.0.1:18099'`를 더한다.
   실행 뒤 통계 파일의 `ok`가 실행한 테스트 수 이상이고 `unexpected`가 0인지 확인하고(`python -I .github/ci/hibp_stub_check.py
   --stats-file <파일> --min-ok <테스트 수>`), 스텁 창을 닫고 통계 파일을 지운다.
