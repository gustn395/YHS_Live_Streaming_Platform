# global — module

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. To-Be 설계

- 패키지: 도메인 모듈의 `in·app·domain·out` 대신 기능별 패키지를 쓴다. global은 업무 규칙이 없는 공통 기반이라서다.

| 패키지 | 내용 | 결정 |
| --- | --- | --- |
| `error` | `ErrorCode`(오류 코드 계약), `CommonErrorCode`, `BusinessException`, `GlobalExceptionHandler`(ProblemDetail + code + errors[]) | G-D003 |
| `security` | `GlobalSecurityConfig`(stateless 기본 체인, CSRF·Access 쿠키 필터 등록), `PublicEndpoint`·`PublicEndpointContributor`(모듈별 공개 경로), 401·403 처리기(MVC 예외 처리기로 위임) | G-D004, G-D005, G-D011, G-D014 |
| `security.csrf` | `CsrfConfig`(쿠키 저장소), `SpaCsrfTokenRequestHandler`(6.5 문서 구성), `CsrfTokenController`(`GET /api/v1/auth/csrf`), `CsrfTokenRenewer`(공개 API) | G-D014 |
| `security.jwt` | `JwtProperties`, `JwtConfig`, `JwtAccessTokenService`, `AccessTokenIssuer`(공개 API)·`AccessTokenVerifier`, `AccessTokenRoles`, `AccessTokenAuthenticationFilter`, `IssuedAccessToken`, `AccessTokenClaims` | MB-D012, G-D006, G-D007 |
| `auth` | `AuthenticatedMember`, `@CurrentMember`와 리졸버, `LoginSessionVerifier`(포트)·`LoginSessionStatus`·`LoginSessionStoreUnavailableException`, `AuthCookieNames`(Access 쿠키 이름만. member 경로에 묶인 Refresh·Challenge 쿠키는 member가 정의) | G-D006, G-D007, MB-D013 |
| `openapi` | 공통 `OpenAPI`(보안 스킴 `accessTokenCookie`·`csrfHeader`), `ProblemDetail` 오류 스키마(`OpenApiCustomizer`), 문서가 켜졌을 때만 문서 경로 공개 | G-D008, G-D012, MB-D013, G-D014 |
| `persistence` | `GlobalJpaConfig`(`@EnableJpaAuditing`), `BaseTimeEntity`(`created_at`·`updated_at` TIMESTAMPTZ를 감사 기능으로 채움) | G-D010 |
| `time` | `TimeConfig`(`Clock`, 다른 Bean이 없을 때 UTC 시스템 시계) | — |

- OpenAPI 보안 스킴: BE-01에서는 정의하지 않았고 BE-03(MB-W05)에서 cookie 방식 `accessTokenCookie`(`__Host-yhs_at`, [MB-D013](../member/decisions.md#1-확정-결정))와 CSRF 헤더 `csrfHeader`(`X-XSRF-TOKEN`)를 정의했다. Bearer 스킴은 넣지 않는다(Issue #8 범위 변경, 2026-11-26 사용자 결정).

## 2. As-Is

2026-11-26, 브랜치 `feature/13-member-login-otp` 기준(공통 기반 #8, JPA 공통 #10, CSRF·JWT #13).

- 산출물: `:app:build`가 만든 WAR 2개(실행 가능, plain)의 `WEB-INF/lib`에 `yhs-live-streaming-global-0.0.1-SNAPSHOT.jar`가 들어간다(2026-11-26 확인).
- 빌드: [modules/global/build.gradle.kts](../../backend/modules/global/build.gradle.kts) — `yhs.domain-module-conventions`, `api`로 `spring-boot-starter-webmvc`·`-security`·`-validation`·`-data-jpa`, `implementation`으로 springdoc 3.1.1·`spring-security-oauth2-jose`(Nimbus). `app`이 `implementation(project(":modules:global"))`로 조립한다.
- 빌드 검사: `yhs.java-conventions`가 실행·테스트 클래스패스의 Boot·Framework 모듈 버전이 하나인지 검사한다(G-D012, GL-T011). 의존성 해석 시점(`afterResolve`)에 실패하므로 IDE의 Gradle 동기화도 같은 메시지로 실패한다.
- 검증 오류: `@Valid @RequestBody`(MethodArgumentNotValid), `@RequestParam`·`@PathVariable` 제약(HandlerMethodValidation), `@Validated` 빈의 메서드 인자 위반(ConstraintViolation, 경로에 PARAMETER 노드)을 400 `COMMON_INVALID_INPUT` + `errors[]`로 응답한다. 반환값 위반(컨트롤러 반환값의 `HandlerMethodValidationException.isForReturnValue()`, `@Validated` 빈의 반환값)·객체(엔티티 저장 직전 등) 검증 위반은 서버 쪽 문제라 500 `COMMON_INTERNAL_ERROR`다. 객체 수준(클래스 제약) 오류는 아직 `errors[]`에 넣지 않는다(클래스 제약을 쓰는 코드가 없음).
- 설정: `app/src/main/resources/application.yml` — `springdoc.api-docs.enabled`·`swagger-ui.enabled` = `${SPRINGDOC_ENABLED:false}`, `spring.autoconfigure.exclude`로 Boot 기본 계정(`UserDetailsServiceAutoConfiguration`) 제외(G-D013).
- 코드: 1장 표의 클래스가 모두 있다. `LoginSessionVerifier` 구현은 member `RedisLoginSessionStore`다.
- CSRF(G-D014): `CookieCsrfTokenRepository.withHttpOnlyFalse()`(Path=/, cookieCustomizer로 Secure·SameSite=Strict, Domain 미지정) + `SpaCsrfTokenRequestHandler`(응답에 노출하는 토큰은 XOR로 가리고, `X-XSRF-TOKEN` 헤더는 쿠키 원문과 비교). 6.5 문서 예시의 "매 요청 지연 토큰 로드"는 넣지 않았다: 초기 토큰은 `GET /api/v1/auth/csrf`로만 발급한다(BE-03 3라운드 Q4). 무시 경로가 없어 공개 POST도 보호된다. 거부는 403 `AUTH_CSRF_INVALID`. `CsrfTokenRenewer`는 같은 이름·Path의 새 쿠키 하나로 덮어쓴다(삭제 쿠키를 따로 보내지 않음). MockMvc의 `Set-Cookie` 문자열은 Servlet `Cookie` 속성(SameSite)을 쓰지 않아, 테스트는 `Cookie` 객체 속성으로 확인한다.
- Access Token(MB-D012): `JwtAccessTokenService`가 `NimbusJwtEncoder`(kid가 있는 `OctetSequenceKey`)·`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`로 발급·검증한다. 검증기는 `JwtTimestampValidator(Duration.ZERO)` + `JwtIssuerValidator` + 필수 Claim 검사(`kid`·`aud`·`exp` 미래·`sid`·`role`·`sub`). Spring `Jwt` 빌더가 `iat`가 없으면 `exp-1초`로 채우므로 `iat` 존재는 토큰 원문(`SignedJWT`)으로 확인한다. 실패 이유는 밖으로 내보내지 않는다.
- Access 쿠키 필터: `AccessTokenAuthenticationFilter`를 `GlobalSecurityConfig`가 `new`로 `AnonymousAuthenticationFilter` 앞에 한 번 등록한다(Bean이 아니라 서블릿 필터로 중복 등록되지 않음). 세션 저장소 예외는 `HandlerExceptionResolver`로 넘겨 503 ProblemDetail로 응답하고 체인을 멈춘다.
- 오류 코드: `COMMON_INVALID_INPUT`(400 검증), `COMMON_INTERNAL_ERROR`(500), `AUTH_UNAUTHENTICATED`(401), `AUTH_ACCESS_DENIED`(403), `AUTH_CSRF_INVALID`(403), `COMMON_TOO_MANY_REQUESTS`(429, `TooManyRequestsException`이면 `Retry-After` 초 올림·최소 1), `AUTH_SESSION_STORE_UNAVAILABLE`(503), `COMMON_RATE_LIMIT_UNAVAILABLE`(503). 프레임워크가 만든 오류는 `COMMON_<HTTP 상태 이름>`(예: `COMMON_NOT_FOUND`, `COMMON_BAD_REQUEST`).
- 설정: `auth.jwt.key`·`key-id`(환경변수 `AUTH_JWT_KEY`·`AUTH_JWT_KEY_ID`, 기본값 없음, 256비트·키 ID 형식이 아니면 기동 실패).
- JPA 공통(GL-W06, Issue #10): global이 `spring-boot-starter-data-jpa`를 `api`로 내놓는다. 감사 기능 동작은 member 영속성 테스트(MB-T010)가 확인한다.
- 테스트: global 단위 테스트(외부 인프라 없음)와 app 통합 테스트. 건수는 [test-spec.md](test-spec.md) 2장이 원본이다. BE-02부터 app 통합 테스트는 Testcontainers PostgreSQL을 쓰므로 Docker가 필요하다(`testsupport.AppIntegrationTest`). 통합 테스트 전용 엔드포인트는 스캔 범위 밖 패키지 `testsupport`에 있다.

## 3. 공개 API

다른 모듈이 쓰는 타입이다. 이 밖의 클래스는 내부 구현이다.

| 타입 | 쓰는 방법 |
| --- | --- |
| `error.ErrorCode` | 모듈 오류 코드를 이 인터페이스의 enum으로 정의한다(`status()`, `code()`) |
| `error.BusinessException` | `throw new BusinessException(errorCode, detail)` → 해당 상태의 ProblemDetail. detail에 민감 정보 금지 |
| `security.PublicEndpointContributor` / `PublicEndpoint` | 인증 없이 열 경로를 Bean으로 등록한다. 등록하지 않은 경로는 인증 필요 |
| `auth.AuthenticatedMember` / `@CurrentMember` | 컨트롤러 인자 `@CurrentMember AuthenticatedMember member`. 인증 주체가 없으면 401 |
| `auth.LoginSessionVerifier` | member가 구현한다. 계약은 인터페이스 Javadoc과 G-D007 |
| `openapi.OpenApiConfig.PROBLEM_DETAIL_SCHEMA` | API Spec의 오류 응답에서 참조할 스키마 이름 |
| `persistence.BaseTimeEntity` | 엔티티가 상속하면 `created_at`·`updated_at`이 자동으로 채워진다. 테이블에 두 컬럼(TIMESTAMPTZ NOT NULL)을 둔다 |
| `error.TooManyRequestsException` | `throw new TooManyRequestsException(errorCode, detail, retryAfter)` → 429 + `Retry-After` |
| `auth.AuthCookieNames` | Access Token 쿠키 이름(`__Host-yhs_at`) |
| `security.jwt.AccessTokenIssuer` | `issue(memberId, role, sessionId, sessionExpiresAt)` → `IssuedAccessToken(value, expiresAt)`. 만료는 15분과 세션 절대 만료 중 이른 쪽 |
| `security.jwt.AccessTokenRoles` | `role` Claim 허용 값을 Bean으로 넘긴다(member가 `MemberRole`로 제공) |
| `security.csrf.CsrfTokenRenewer` | 인증 상태가 바뀐 응답에서 `renew(request, response)`로 새 `XSRF-TOKEN`을 준다 |

## 4. 다른 모듈 의존

- 없음. global은 도메인 모듈에 의존하지 않는다. 도메인 모듈이 global에 의존한다.

## 5. 데이터 모델

- 해당 없음 (영속 데이터 없음)
