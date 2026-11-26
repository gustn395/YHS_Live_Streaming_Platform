# global — regression-log

실제로 실행한 테스트 결과만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 실행 기록

| 일시 (KST) | 명령 | 결과 | 비고 |
| --- | --- | --- | --- |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --continue` (구현 전, RED 확인) | global 20건 중 19 실패, app 17건 전부 실패 | global은 기능 없음으로 실패(의도). app은 컨텍스트 로드 실패(의도 아님) → 원인: springdoc 3.2.1이 Boot 4.2.0 모듈을 끌어옴(G-D012), 테스트 컨트롤러 중복 등록 |
| 2026-11-26 | `.\gradlew.bat :app:test --tests com.yhs.livestreaming.LiveStreamingApplicationTests` (버전 검사 추가, springdoc 3.2.1) | 실패(의도) | GL-T011: "Spring 버전 불일치(testRuntimeClasspath)" — Boot 4.2.0 모듈 10개, Framework 7.1.0/7.0.11 |
| 2026-11-26 | 같은 명령 (springdoc 3.1.1) | 성공, 3건 통과 | 중복 관측 convention 빈 사라짐 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --continue` (구현 후) | 성공. global 20건, app 18건 전부 통과 | `GlobalExceptionHandlerTest` 9, `CurrentMemberArgumentResolverTest` 5, `AuthenticatedMemberTest` 4, `SecurityErrorHandlersTest` 2, `SecurityChainIntegrationTest` 11, `LiveStreamingApplicationTests` 3, `OpenApiEnabledIntegrationTest` 2, `OpenApiDisabledByDefaultIntegrationTest` 2. 테스트 로그에 생성 비밀번호 0건 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --rerun-tasks --continue` (S5 검증) | 성공(exit 0), 38건 통과 | 비밀 값 패턴 grep 0건 |
| 2026-11-26 | 같은 명령, 리뷰(S6) 지적 확인용 테스트 4건 추가 후 (수정 전) | global 23건 중 2 실패(의도) | `@RequestParam` 제약 → `COMMON_BAD_REQUEST`(errors 없음), `ConstraintViolationException` → 500. `text/html` Accept 테스트 2건은 수정 없이 통과(지적 기각) |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --continue` (수정 후) | 성공(exit 0), 42건 통과(global 23, app 19) | |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --tests com.yhs.livestreaming.global.error.GlobalExceptionHandlerTest` (반환값·객체 검증 위반 테스트 추가, 수정 전) | 14건 중 2 실패(의도) | 반환값 위반·객체 검증 위반이 500이 아니라 400 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --continue` (수정 후) | 성공(exit 0), 44건 통과(global 25, app 19) | |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --rerun-tasks --continue` (⑤ 전 최종 검증) | 성공(exit 0), 44건 통과 | 비밀 값 패턴 grep 0건. 보안 리뷰(S10) 취약점 0건 |
| 2026-11-26 | `.\gradlew.bat :app:build` (Commit 직전 App build) | 성공(exit 0) | `:app:test` UP-TO-DATE(직전 `--rerun-tasks` 실행 결과 재사용, app 19건 통과), `bootWar`·`war` 새로 생성. WAR 2개 모두 `WEB-INF/lib`에 global 포함 모듈 jar 8개 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --tests com.yhs.livestreaming.global.error.GlobalExceptionHandlerTest` (CodeRabbit 지적 확인용 테스트 추가, 수정 전) | 15건 중 1 실패(의도) | 컨트롤러 반환값 `@NotBlank` 위반이 500이 아니라 400 → 지적 재현 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :app:test --continue` (수정 후) | 성공(exit 0), 45건 통과(global 26, app 19) | |
| 2026-11-26 | (#13) `.\gradlew.bat :modules:global:test --tests "*GlobalExceptionHandlerTest"`, `:app:test --tests "*CsrfIntegrationTest"`, `:modules:global:test --tests "*security.jwt*"` (테스트 먼저, RED) | 컴파일 실패 또는 실패(의도) | 429·CSRF 코드, `/csrf`·CSRF 활성화, JWT 발급·검증·필터 없음. CSRF 쿠키 속성은 MockMvc `Set-Cookie` 문자열에 SameSite가 없어 `Cookie` 속성으로 단언을 바꿈 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --tests "*security.jwt*"` (구현 1차) | 19건 중 2 실패 | HS512 서명에 256비트 키(테스트 구성 문제), `iat` 누락 토큰 통과(원인: Spring `Jwt` 빌더가 `iat`를 `exp-1초`로 채움 → 토큰 원문으로 확인) |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --rerun :modules:member:test --rerun :app:test --rerun --continue` (S5) | 성공(exit 0). global 54, app 69(global 쪽 32: `SecurityChainIntegrationTest` 11, `CsrfIntegrationTest` 9, `AccessTokenCookieIntegrationTest` 5, `LiveStreamingApplicationTests` 3, OpenAPI 4) 통과 | member는 member regression-log |

- 미실행: 로컬 전체 `./gradlew clean build`(빌드 정책상 실행하지 않음, CI가 실행), 실행 가능한 WAR 기동·외부 Tomcat 배포 확인.
