# global — test-spec

요구사항·합격 기준(`GL-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

| ID | 요구사항 | 합격 기준 | 근거 | 테스트 |
| --- | --- | --- | --- | --- |
| GL-T001 | 보호 경로의 익명 요청은 401 ProblemDetail | 401, `application/problem+json`, `code=AUTH_UNAUTHENTICATED`. 필터 단계 401은 MVC 예외 처리기로 넘긴다 | G-D003, G-D004, R-22 | `SecurityChainIntegrationTest`, `SecurityErrorHandlersTest` |
| GL-T002 | 인증됐지만 권한이 없으면 403, 익명의 접근 거부는 401 | 403 `code=AUTH_ACCESS_DENIED` / 익명 401 `AUTH_UNAUTHENTICATED`. 필터 단계 403도 같은 처리기 | G-D003 | `SecurityChainIntegrationTest`, `GlobalExceptionHandlerTest`, `SecurityErrorHandlersTest` |
| GL-T003 | health 공개, 나머지 Actuator는 비노출 | `/actuator/health` 익명 200. `/actuator/env`·`beans` 익명 401, 인증 404(`COMMON_NOT_FOUND`) | README Q4, G-D011 | `SecurityChainIntegrationTest`, `LiveStreamingApplicationTests` |
| GL-T004 | 요청 검증 오류는 400 + errors[] | `@Valid` 본문·`@RequestParam` 제약·`@Validated` 빈의 메서드 인자 위반 모두 400(반환값·객체 검증 위반은 GL-T005의 500), `code=COMMON_INVALID_INPUT`, `errors[].field`·`message`. 잘못된 JSON은 400 `COMMON_BAD_REQUEST` | G-D003 | `GlobalExceptionHandlerTest` |
| GL-T005 | 처리하지 않은 예외는 500, 내부 정보 비노출 | 500 `COMMON_INTERNAL_ERROR`, 본문에 예외 메시지·클래스 이름 없음. 클라이언트가 `text/html`만 받아도 오류는 problem+json | G-D003 | `GlobalExceptionHandlerTest`, `SecurityChainIntegrationTest` |
| GL-T006 | stateless, CSRF는 쿠키 인증 전까지 비활성, 기본 계정 없음 | 익명·인증 요청 모두 세션·`Set-Cookie` 없음. 토큰 없는 공개 POST가 403이 아님. `UserDetailsService` Bean 없음 | G-D004, G-D005, G-D013 | `SecurityChainIntegrationTest` |
| GL-T007 | 인증 계약 | `AuthenticatedMember`는 memberId 양수·role·sessionId 필수. `@CurrentMember`는 인증 주체를 주입하고 없으면 401 | G-D006 | `AuthenticatedMemberTest`, `CurrentMemberArgumentResolverTest`, `SecurityChainIntegrationTest` |
| GL-T008 | springdoc을 켜면 문서·Swagger UI 공개, 공통 오류 스키마 포함 | `/v3/api-docs` 익명 200, `ProblemDetail` 스키마에 `code`·`errors`·`status`. `/swagger-ui/index.html` 200 | G-D008 | `OpenApiEnabledIntegrationTest` |
| GL-T009 | 운영 기본 설정에서는 문서 비활성 | `SPRINGDOC_ENABLED` 미설정 시 `/v3/api-docs` 익명 401, 인증 404, Swagger UI 인증 404 | G-D008 | `OpenApiDisabledByDefaultIntegrationTest` |
| GL-T010 | 업무 오류·세션 저장소 오류·프레임워크 오류에도 code | `BusinessException`은 자기 `ErrorCode`의 상태·code·detail. 세션 저장소 오류 503 `AUTH_SESSION_STORE_UNAVAILABLE`(접속 정보 비노출). 없는 경로 404 `COMMON_NOT_FOUND` | G-D003, G-D007 | `GlobalExceptionHandlerTest` |
| GL-T011 | Spring 모듈 버전이 섞이면 빌드 실패 | 실행·테스트 클래스패스에서 `org.springframework.boot` 모듈이 Boot 버전과 다르거나 Framework 모듈이 두 버전 이상이면 의존성 해석 실패 | G-D012 | Gradle 검사(`yhs.java-conventions`) — springdoc 3.2.1로 실패, 3.1.1로 통과를 확인 |

## 2. 집계

- 요구사항 11건(GL-T001~GL-T011). JUnit 테스트 44건(global 25, app 19). 실행 결과는 [regression-log.md](regression-log.md).
