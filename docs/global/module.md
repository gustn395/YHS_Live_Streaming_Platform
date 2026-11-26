# global — module

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. To-Be 설계

- 패키지: 도메인 모듈의 `in·app·domain·out` 대신 기능별 패키지를 쓴다. global은 업무 규칙이 없는 공통 기반이라서다.

| 패키지 | 내용 | 결정 |
| --- | --- | --- |
| `error` | `ErrorCode`(오류 코드 계약), `CommonErrorCode`, `BusinessException`, `GlobalExceptionHandler`(ProblemDetail + code + errors[]) | G-D003 |
| `security` | `GlobalSecurityConfig`(stateless 기본 체인), `PublicEndpoint`·`PublicEndpointContributor`(모듈별 공개 경로), 401·403 처리기(MVC 예외 처리기로 위임) | G-D004, G-D005, G-D011 |
| `auth` | `AuthenticatedMember`, `@CurrentMember`와 리졸버, `LoginSessionVerifier`(포트)·`LoginSessionStatus`·`LoginSessionStoreUnavailableException` | G-D006, G-D007 |
| `openapi` | 공통 `OpenAPI`, `ProblemDetail` 오류 스키마(`OpenApiCustomizer`), 문서가 켜졌을 때만 문서 경로 공개 | G-D008, G-D012 |

- 다음 작업에서 더할 것: JWT 검증 필터와 CSRF 활성화(MB-W05, G-U02), `LoginSessionVerifier` 구현(member, MB-W05~W08), DB 공통 설정(BE-02, G-D010).
- OpenAPI 보안 스킴: BE-01에서는 정의하지 않는다. 실제 인증 쿠키의 이름·속성이 정해지는 BE-03(MB-W05)에서 cookie 방식 `securitySchemes`를 정의한다. 가칭 쿠키 스킴이나 Bearer 스킴은 넣지 않는다(Issue #8 범위 변경, 2026-11-26 사용자 결정).

## 2. As-Is

2026-11-26, 브랜치 `feature/8-global-common-foundation` 기준.

- 산출물: `:app:build`가 만든 WAR 2개(실행 가능, plain)의 `WEB-INF/lib`에 `yhs-live-streaming-global-0.0.1-SNAPSHOT.jar`가 들어간다(2026-11-26 확인).
- 빌드: [modules/global/build.gradle.kts](../../backend/modules/global/build.gradle.kts) — `yhs.domain-module-conventions`, `api`로 `spring-boot-starter-webmvc`·`-security`·`-validation`, `implementation`으로 springdoc 3.1.1. `app`이 `implementation(project(":modules:global"))`로 조립한다.
- 빌드 검사: `yhs.java-conventions`가 실행·테스트 클래스패스의 Boot·Framework 모듈 버전이 하나인지 검사한다(G-D012, GL-T011). 의존성 해석 시점(`afterResolve`)에 실패하므로 IDE의 Gradle 동기화도 같은 메시지로 실패한다.
- 검증 오류: `@Valid @RequestBody`(MethodArgumentNotValid), `@RequestParam`·`@PathVariable` 제약(HandlerMethodValidation), `@Validated` 빈의 메서드 인자 위반(ConstraintViolation, 경로에 PARAMETER 노드)을 400 `COMMON_INVALID_INPUT` + `errors[]`로 응답한다. 반환값 위반(컨트롤러 반환값의 `HandlerMethodValidationException.isForReturnValue()`, `@Validated` 빈의 반환값)·객체(엔티티 저장 직전 등) 검증 위반은 서버 쪽 문제라 500 `COMMON_INTERNAL_ERROR`다. 객체 수준(클래스 제약) 오류는 아직 `errors[]`에 넣지 않는다(클래스 제약을 쓰는 코드가 없음).
- 설정: `app/src/main/resources/application.yml` — `springdoc.api-docs.enabled`·`swagger-ui.enabled` = `${SPRINGDOC_ENABLED:false}`, `spring.autoconfigure.exclude`로 Boot 기본 계정(`UserDetailsServiceAutoConfiguration`) 제외(G-D013).
- 코드: 1장 표의 클래스가 모두 있다. JWT 필터·`LoginSessionVerifier` 구현은 없다.
- 오류 코드: `COMMON_INVALID_INPUT`(400 검증), `COMMON_INTERNAL_ERROR`(500), `AUTH_UNAUTHENTICATED`(401), `AUTH_ACCESS_DENIED`(403), `AUTH_SESSION_STORE_UNAVAILABLE`(503). 프레임워크가 만든 오류는 `COMMON_<HTTP 상태 이름>`(예: `COMMON_NOT_FOUND`, `COMMON_BAD_REQUEST`).
- 테스트: global 단위 테스트 4클래스 26건(외부 인프라 없음), app 통합 테스트 4클래스 19건(Docker 없음). 통합 테스트 전용 엔드포인트는 스캔 범위 밖 패키지 `testsupport`에 있다.

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

## 4. 다른 모듈 의존

- 없음. global은 도메인 모듈에 의존하지 않는다. 도메인 모듈이 global에 의존한다.

## 5. 데이터 모델

- 해당 없음 (영속 데이터 없음)
