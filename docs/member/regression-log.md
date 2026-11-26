# member — regression-log

실제로 실행한 테스트 결과만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 실행 기록

| 일시 (KST) | 명령 | 결과 | 비고 |
| --- | --- | --- | --- |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :modules:member:test :app:test --continue` (구현 전, RED 확인) | member 37건 중 34 실패, app 28건 중 9 실패 | member는 기능 없음, 영속성 테스트는 "Schema validation: missing table [members]"(의도). app은 회원가입 엔드포인트가 없어 401(의도). 기존 app 테스트 19건은 Testcontainers 적용 상태로 통과 |
| 2026-11-26 | 같은 명령 (구현 후) | 성공(exit 0). global 26, member 37, app 28건 통과 | |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests com.yhs.livestreaming.member.out.MemberRepositoryAdapterTest` (기타 제약 위반 테스트 추가) | 성공, 8건 통과 | VARCHAR(255) 초과는 `DataIntegrityViolationException`으로 남고 409로 바뀌지 않음 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (S6 지적 회귀 테스트 추가, 수정 전 RED) | member 1 실패, app 5 실패 | 어댑터 예외 메시지에 이메일, 닉네임 Cc·Cf·이모지 길이, Kelvin 기호 username, 동시 가입 로그에 이메일. 1건(비밀번호 정책 로그 검사)은 다른 실패 테스트의 요청 본문 출력 때문 |
| 2026-11-26 | 같은 명령 (수정 1차) | member 38건 통과, app 31건 중 1 실패 | `SqlExceptionHelper` 로거를 껐지만 Hibernate 7은 `org.hibernate.orm.jdbc.error` 카테고리로 남겨 이메일이 로그에 남음 |
| 2026-11-26 | 같은 명령 (로거 이름 수정 뒤) | 성공(exit 0). member 38건, app 31건(member 12건 포함) 통과 | member는 증분 빌드로 UP-TO-DATE(직전 실행에서 통과) |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --rerun :app:test --continue` (S5 재검증) | 성공(exit 0). member 38건 다시 실행·통과, app UP-TO-DATE(직전 통과) | |
| 2026-11-26 | `.\gradlew.bat :app:build` (Commit 직전) | 성공(exit 0). WAR 2개(실행 가능·plain), `WEB-INF/lib`에 member·global JAR 포함. `:app:test` UP-TO-DATE | ⑤ 실행 전 사용자 중단(커밋 없음), HIBP 연동 추가로 이어짐 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (HIBP 테스트 작성 후, 구현 전 RED) | 컴파일 실패 | `BreachedPasswordChecker`·`HibpPwnedPasswordsClient`·`MemberErrorCode.PASSWORD_*`·3인자 `SignupService` 없음, `PasswordPolicy` 1인자 생성자(의도) |
| 2026-11-26 | 같은 명령 (HIBP 구현·로컬 차단 목록 제거 후) | 성공(exit 0). member 50건(단위 42, 영속성 8), app 33건(member 14) 통과 | 실제 HIBP 호출 없음(스텁·대역) |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --rerun :app:test --rerun --continue` (S5) | 성공(exit 0) | 결과 XML에 `api.pwnedpasswords.com`·`/range/` 없음 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests "*HibpPwnedPasswordsClientTest" :app:test --tests "*SignupApiIntegrationTest" --continue` (S6 지적 회귀 테스트, 수정 전 RED) | member 16건 중 1 실패 | `invalidBaseUrlIsUnavailableWithoutLeak`: 요청 생성의 `IllegalArgumentException`이 밖으로 나감(의도) |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (S6 수정 후) | 성공(exit 0). member 54건(단위 46, 영속성 8), app 33건(member 14) 통과 | |
| 2026-11-26 | `.\gradlew.bat :app:build` (Commit 직전, HIBP 반영) | 성공(exit 0). WAR 2개, `WEB-INF/lib`에 member·global JAR 포함, 차단 목록 리소스 없음. `:app:test` UP-TO-DATE(직전 통과) | |
| 2026-11-26 | (실행 기록 아님) | 위 결과로 커밋 `b24c0c2`, Push | |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests "*PasswordPolicyTest" --tests "*HibpPwnedPasswordsClientTest" --continue` (CodeRabbit 지적 회귀 테스트, 수정 전 RED) | 29건 중 2 실패 | `rejectsPasswordEqualToDigitsOnlyUsername`, `stripsTrailingDigitsByCodePoint`(지적 재현). 리다이렉트·본문 정지 테스트는 통과(회귀 감시용) |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (CodeRabbit 반영 후) | 성공(exit 0). member 58건(단위 50, 영속성 8), app 33건 통과 | |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests "*PasswordPolicyTest" --tests "*HibpPwnedPasswordsClientTest" --continue` (재리뷰 지적 회귀 테스트, 수정 전 RED) | 30건 중 1 실패 | `rejectsReversedOrDecoratedDigitsOnlyUsername`. 본문 정지 테스트(하한 1,000ms·`timeout` 로그)는 통과 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (재리뷰 반영 후) | 성공(exit 0). member 59건(단위 51, 영속성 8), app 33건 통과 | 결과 XML에 실제 HIBP 호출 흔적 없음 |
| 2026-11-26 | `.\gradlew.bat :app:build` (Commit 직전, CodeRabbit 반영) | 성공(exit 0). `:app:test` UP-TO-DATE(직전 통과) | |
| 2026-11-26 | (#13 BE-03) Redis·서비스·SMTP·통합 테스트를 단계마다 먼저 작성해 실행(RED) | 컴파일 실패 또는 실패(의도): 포트·어댑터·서비스·엔드포인트 없음. `LoginApiIntegrationTest` 18건은 구성 미연결로 컨텍스트 실패 | 의도하지 않은 실패 3건은 아래 비고 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests "*out.redis*"` (1차) | 42건 중 27 실패 | `@Container` 컨테이너가 클래스마다 재시작되는데 테스트 컨텍스트는 캐시돼 이전 포트로 연결 실패 → JVM 싱글턴 컨테이너로 바꾼 뒤 통과 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test :modules:member:test :app:test` (구현 후 1차) | global 54·member 141 통과, app 69건 중 17 실패 | 가입 테스트가 같은 IP로 6회 이상 가입(새 IP 제한), 메일 health(SMTP 127.0.0.1:9)로 health 503, `LoginApiIntegrationTest`의 spring-security-test `csrf()`가 공유 컨텍스트의 CSRF 저장소를 바꿔 `CsrfIntegrationTest` 실패 → 요청별 IP, 메일 health 끔, 실제 `/csrf` 토큰 사용 |
| 2026-11-26 | `.\gradlew.bat :app:test` (수정 후) | 성공(exit 0). app 69건 통과 | |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --rerun :modules:member:test --rerun :app:test --rerun --continue` (S5) | 성공(exit 0). global 54, member 145(단위 89, 영속성 11, Redis 42, SMTP 3), app 69(member 쪽 37) 통과, 건너뜀 0 | Docker(PostgreSQL·Redis·Mailpit 컨테이너) 사용. 실제 HIBP·Gmail 호출 없음 |
| 2026-11-26 | `.\gradlew.bat :modules:member:compileTestJava :app:compileTestJava` (S6 지적 회귀 테스트를 먼저 수정) | 컴파일 실패 36건(의도) | 숫자 지문·보상 실패·설정 간격·Clock 주입·쿠키 이름 이동이 없음 |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --rerun :modules:member:test --rerun :app:test --rerun --continue` (S6 반영 후 S5) | 성공(exit 0). global 54, member 150(단위 91, 영속성 11, Redis 45, SMTP 3), app 69 통과, 건너뜀 0 | |
| 2026-11-26 | `.\gradlew.bat :modules:member:compileTestJava` (S6·S10 후 추가 확인: 발송 미확인 OTP 검증·실패 메일 숫자 재사용 회귀 테스트 먼저) | 컴파일 실패 10건(의도) | `confirmDelivered`·`recentDigests` 없음. 코드 확인으로 결함 재현: 재발송 OTP가 저장 즉시 검증 가능, 실패 메일 숫자의 지문을 복원으로 지움 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test :app:test --continue` (수정 후) | 성공(exit 0). member 157(단위 95, 영속성 11, Redis 48, SMTP 3), app 69 통과 | |
| 2026-11-26 | `.\gradlew.bat :modules:global:test --rerun :modules:member:test --rerun :app:test --rerun :app:build --continue` (⑤ 직전 최종 S5 + Commit 직전 App build) | 성공(exit 0). global 54, member 157, app 69 통과, 건너뜀 0. WAR 2개(실행 가능·plain), `WEB-INF/lib`에 member·global JAR, `spring-security-oauth2-jose-7.1.3`·`nimbus-jose-jwt-10.9.1`·`spring-data-redis-4.1.3`·`angus-mail-2.0.5` 포함 | clean·전체 build 미실행(빌드 정책) |
| 2026-11-26 | (#13 Phase 5, CodeRabbit) `.\gradlew.bat :modules:member:test --tests "*MemberLimitPropertiesTest" --tests "*OtpServiceTest"` (지적 회귀 테스트 먼저) | 2건 실패(의도) | `max`만 설정하면 `window=null`, 태국 숫자 로케일에서 OTP가 `๐๐๔๒` |
| 2026-11-26 | `.\gradlew.bat :modules:member:test` / `:app:test --continue` (CodeRabbit 반영 후) | 성공(exit 0). member 161, app 72 통과 | 테스트 보강: 쿠키 Max-Age 범위, 응답 JSON 필드 단위 비노출, 메일 실패 원문 비노출, 소진 안내, 동시 OTP 확인, 부분 저장소 장애, 실제 CSRF 토큰, PEXPIRETIME, 연결 팩터리 정리 |
| 2026-11-26 | (Phase 5 S6 재리뷰·Q2) `.\gradlew.bat :modules:member:test --tests "*MemberLimitPropertiesTest"` (빠진 max 회귀 테스트 먼저) | 2건 실패(의도) | 기간만 설정하면 `max=0`, `max=0`이 기동됨 |
| 2026-11-26 | `.\gradlew.bat :modules:member:test --tests "*MemberLimitPropertiesTest" --tests "*RedisRequestRateLimiterTest"` (수정 후) | 성공. 5건, 7건 통과 | 재리뷰 수정(테스트 카운터 원자화·초기화, 동시성 테스트 타임아웃, 테스트 이름)은 직전 전체 재실행에서 통과 |
| 2026-11-26 | (수동 확인) 개발 브라우저 `__Host-`·Secure 쿠키 동작 | 미실행 | MB-D013 확인 항목. 자동 테스트와 별도로 기록한다 |

- 미실행: 로컬 전체 `./gradlew clean build`(빌드 정책상 실행하지 않음).
