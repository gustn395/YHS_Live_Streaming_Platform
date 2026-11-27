# global — test-spec

요구사항·합격 기준(`GL-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

| ID | 요구사항 | 합격 기준 | 근거 | 테스트 |
| --- | --- | --- | --- | --- |
| GL-T001 | 보호 경로의 익명 요청은 401 ProblemDetail | 401, `application/problem+json`, `code=AUTH_UNAUTHENTICATED`. 필터 단계 401은 MVC 예외 처리기로 넘긴다 | G-D003, G-D004, R-22 | `SecurityChainIntegrationTest`, `SecurityErrorHandlersTest` |
| GL-T002 | 인증됐지만 권한이 없으면 403, 익명의 접근 거부는 401 | 403 `code=AUTH_ACCESS_DENIED` / 익명 401 `AUTH_UNAUTHENTICATED`. 필터 단계 403도 같은 처리기 | G-D003 | `SecurityChainIntegrationTest`, `GlobalExceptionHandlerTest`, `SecurityErrorHandlersTest` |
| GL-T003 | health 공개, 나머지 Actuator는 비노출 | `/actuator/health` 익명 200. `/actuator/env`·`beans` 익명 401, 인증 404(`COMMON_NOT_FOUND`) | README Q4, G-D011 | `SecurityChainIntegrationTest`, `LiveStreamingApplicationTests` |
| GL-T004 | 요청 검증 오류는 400 + errors[] | `@Valid` 본문·`@RequestParam` 제약·`@Validated` 빈의 메서드 인자 위반 모두 400(반환값·객체 검증 위반은 GL-T005의 500), `code=COMMON_INVALID_INPUT`, `errors[].field`·`message`. 잘못된 JSON은 400 `COMMON_BAD_REQUEST` | G-D003 | `GlobalExceptionHandlerTest` |
| GL-T005 | 처리하지 않은 예외는 500, 내부 정보 비노출 | 500 `COMMON_INTERNAL_ERROR`, 본문에 예외 메시지·클래스 이름 없음. 컨트롤러·`@Validated` 빈의 반환값 위반과 객체 검증 위반도 500(`errors` 없음). 클라이언트가 `text/html`만 받아도 오류는 problem+json | G-D003 | `GlobalExceptionHandlerTest`, `SecurityChainIntegrationTest` |
| GL-T006 | stateless, 기본 계정 없음 | 익명·인증 요청 모두 세션·`Set-Cookie` 없음(CSRF 토큰도 요청 때마다 내보내지 않음). `UserDetailsService` Bean 없음. CSRF 보호는 GL-T014(BE-03에서 "토큰 없는 공개 POST 허용" 단언을 대체) | G-D004, G-D013 | `SecurityChainIntegrationTest` |
| GL-T007 | 인증 계약 | `AuthenticatedMember`는 memberId 양수·role·sessionId 필수. `@CurrentMember`는 인증 주체를 주입하고 없으면 401 | G-D006 | `AuthenticatedMemberTest`, `CurrentMemberArgumentResolverTest`, `SecurityChainIntegrationTest` |
| GL-T008 | springdoc을 켜면 문서·Swagger UI 공개, 공통 오류 스키마 포함 | `/v3/api-docs` 익명 200, `ProblemDetail` 스키마에 `code`·`errors`·`status`. `/swagger-ui/index.html` 200 | G-D008 | `OpenApiEnabledIntegrationTest` |
| GL-T009 | 운영 기본 설정에서는 문서 비활성 | `SPRINGDOC_ENABLED` 미설정 시 `/v3/api-docs` 익명 401, 인증 404, Swagger UI 인증 404 | G-D008 | `OpenApiDisabledByDefaultIntegrationTest` |
| GL-T010 | 업무 오류·세션 저장소 오류·프레임워크 오류에도 code | `BusinessException`은 자기 `ErrorCode`의 상태·code·detail. 세션 저장소 오류 503 `AUTH_SESSION_STORE_UNAVAILABLE`(접속 정보 비노출). 없는 경로 404 `COMMON_NOT_FOUND` | G-D003, G-D007 | `GlobalExceptionHandlerTest` |
| GL-T011 | Spring 모듈 버전이 섞이면 빌드 실패 | 실행·테스트 클래스패스에서 `org.springframework.boot` 모듈이 Boot 버전과 다르거나 Framework 모듈이 두 버전 이상이면 의존성 해석 실패 | G-D012 | Gradle 검사(`yhs.java-conventions`) — springdoc 3.2.1로 실패, 3.1.1로 통과를 확인 |
| GL-T012 | Access Token(JWT) 발급·검증 | 발급 Claim·헤더(`alg=HS256`·`kid`·`iss`·`aud`·`sub`·`sid`·`role`·`iat`·`exp`, `jti`·개인정보 없음), Access `exp` ≤ 세션 절대 만료, 끝난 세션 발급 거부. 검증 거부: `exp` 정각(시계 오차 0초), 서명 변조·다른 키·다른 `kid`·HS512·`alg=none`, 다른 `iss`·`aud`, `sid`·`exp`·`iat` 누락(Spring `Jwt`가 `iat`를 `exp-1초`로 채우는 것과 무관하게 원문 확인), `sid` 형식·타입, `sub` 숫자 아님·0·음수, 허용되지 않은 `role`·타입, 쓰레기 값(예외 없음) | MB-D012 | `JwtAccessTokenServiceTest` |
| GL-T013 | Access 쿠키 인증 필터 | `__Host-yhs_at`만 읽음(`Authorization` 무시), 쿠키 없음·잘못된 토큰 → 익명 계속(공개 API 정상, 보호 API 401), 유효 + 세션 ACTIVE → `AuthenticatedMember`·`ROLE_<role>`, 세션 NOT_FOUND·MEMBER_MISMATCH → 익명, 세션 저장소 오류 → 체인 중단·503 ProblemDetail. 보안 체인에 한 번만 있고 서블릿 필터·Bean으로 따로 등록되지 않음 | G-D004, G-D006, G-D007, MB-D012 | `AccessTokenAuthenticationFilterTest`, `AccessTokenCookieIntegrationTest`, `RedisUnavailableIntegrationTest` |
| GL-T014 | CSRF SPA 이중 제출 | `GET /api/v1/auth/csrf` 204·본문 없음·`Cache-Control: no-store`·`XSRF-TOKEN`(HttpOnly 아님·Secure·SameSite=Strict·Path=/·Domain 없음). 발급받은 쿠키+`X-XSRF-TOKEN` 헤더로 공개·보호 POST 통과. 토큰 없음·쿠키만·헤더만·서로 다른 값 → 403 `AUTH_CSRF_INVALID`(익명도 403). GET은 토큰 불필요 | G-D014, G-D005 | `CsrfIntegrationTest`, `GlobalExceptionHandlerTest` |
| GL-T015 | OpenAPI 보안 스킴 | `components.securitySchemes`: `accessTokenCookie`(apiKey, cookie, `__Host-yhs_at`), `csrfHeader`(apiKey, header, `X-XSRF-TOKEN`) | G-D008, MB-D013, G-D014 | `AuthOpenApiIntegrationTest` |
| GL-T016 | JWT 키 기동 검증 | `auth.jwt.key` 누락·Base64 아님·256비트 아님, `key-id` 누락·형식 오류 → 기동 실패, 메시지·`toString`에 키 값 없음 | MB-D007, MB-D012 | `JwtPropertiesTest` |
| GL-T017 | 429·CSRF 오류 응답 | `TooManyRequestsException` → 429 + `Retry-After`(초 올림, 최소 1), `COMMON_TOO_MANY_REQUESTS`. CSRF 예외 → 403 `AUTH_CSRF_INVALID` | G-D003, MB-D017, MB-D018 | `GlobalExceptionHandlerTest` |
| GL-T018 | 인증된 요청이 CSRF 토큰을 지우지 않음 | 실제 Access 쿠키로 인증된 상태 변경 요청(POST)의 응답에 `XSRF-TOKEN` `Set-Cookie`가 없고, 같은 CSRF 토큰으로 다음 요청도 성공. 갱신은 `CsrfTokenRenewer` 호출 지점(로그인 최종 성공·로그아웃)에서만 | G-D014, MB-D020, MB-D022 | `AccessTokenCookieIntegrationTest`, member `RefreshLogoutApiIntegrationTest` |
| GL-T019 | 모듈 경계(ArchUnit) | 운영 코드(테스트 제외) 전체에서 member 밖의 코드는 member 중 `member.app.api`만 참조, global은 도메인 모듈(member·broadcast·streaming·chat·comment·donation·recording)을 참조하지 않음. 규칙이 실제 위반(테스트 전용 `ForbiddenMemberConsumer`가 `member.domain.Member` 참조)을 잡고 공개 인터페이스만 쓰는 대상(`AllowedMemberConsumer`)은 통과시킴. `member.app.api`는 프로젝트 안에서 자기 패키지만 참조(내부 타입 노출 금지, 위반 대상 `LeakyPublicContract`는 잡고 실제 공개 타입은 통과). global 규칙도 위반 대상(`ForbiddenGlobalDependency`가 member 공개 인터페이스 참조)을 잡음. 운영 클래스를 실제로 읽었는지(member·global 클래스 포함, 테스트 대상 제외) 확인 | G-D015, G-D001 | `ModuleBoundaryTest` |

## 2. 집계

- 요구사항 19건(GL-T001~GL-T019). JUnit 테스트 94건(global 54, app 40: `SecurityChainIntegrationTest` 11, `CsrfIntegrationTest` 9, `AccessTokenCookieIntegrationTest` 6, `LiveStreamingApplicationTests` 3, OpenAPI 4, `ModuleBoundaryTest` 7). GL-T015는 `AuthOpenApiIntegrationTest`(member 쪽 app 테스트로 집계)가 확인한다. 실행 결과는 [regression-log.md](regression-log.md).
