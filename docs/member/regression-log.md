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

- 미실행: 로컬 전체 `./gradlew clean build`(빌드 정책상 실행하지 않음).
