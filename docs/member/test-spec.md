# member — test-spec

요구사항·합격 기준(`MB-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

명세 열은 Member 기준 명세의 기능 번호(M-xx)와 테스트 번호(MEM-xx)다([MB-D001](decisions.md#1-확정-결정)).

| ID | 요구사항 | 합격 기준 | 명세 | 근거 | 테스트 |
| --- | --- | --- | --- | --- | --- |
| MB-T001 | 정상 회원가입 | 201, `accountStatus=PENDING`. DB에 LOCAL·PENDING·`email_verified=false`·USER, username·이메일 소문자, 닉네임 앞뒤 공백 제거. `Set-Cookie`·토큰 없음 | M-01, MEM-01 | MB-D003, MB-D008 | `SignupApiIntegrationTest`, `MemberTest` |
| MB-T002 | 중복 username | 409 `MEMBER_USERNAME_DUPLICATE`(대소문자만 다른 경우 포함), 저장 건수 1 | M-01, MEM-02 | MB-D008 | `SignupApiIntegrationTest`, `MemberRepositoryAdapterTest` |
| MB-T003 | 중복 이메일(LOCAL끼리) | 409 `MEMBER_EMAIL_DUPLICATE`, 저장 건수 1. GOOGLE 계정과는 같은 이메일 허용 | M-01, M-10, MEM-09 | MB-D008 | `SignupApiIntegrationTest`, `MemberRepositoryAdapterTest` |
| MB-T004 | 동시 가입 | 같은 username 또는 같은 이메일로 8건 동시 요청 → 201 1건, 나머지 409, DB 1건. DB 제약 위반 상세(이메일 등)가 로그에 남지 않음 | M-01 | MB-D008 | `SignupApiIntegrationTest` |
| MB-T005 | 입력 형식 검증 | username·이메일·닉네임(길이·제어 문자) 오류, 닉네임의 Cc·Cf 문자(U+0085·U+202E·U+200B), 닉네임 길이는 code point 기준(이모지 20자 허용·21자 거부), 소문자로 바꾸면 ASCII가 되는 비ASCII username(U+212A KELVIN SIGN) 거부, 비밀번호 확인 불일치 → 400 `COMMON_INVALID_INPUT` + 필드별 `errors[]`, 저장 없음 | M-01, MEM-03 | MB-D008, G-D003 | `SignupApiIntegrationTest` |
| MB-T006 | 비밀번호 정책 | NFC 뒤 code point 15~64자, 공백 유지·Trim 없음, username과 같은 값·뒤집은 값·기호를 덧붙인 값(숫자·밑줄만인 username 포함)·username·서비스 이름 파생어 거부, 끝 숫자는 code point 단위로 제거(보충 평면 숫자 U+1D7D9 포함) → 400 `errors[].field=password`. 응답·로그에 비밀번호 원문 없음 | M-02 | MB-D004, MB-D005 | `PasswordPolicyTest`, `SignupApiIntegrationTest` |
| MB-T007 | 비밀번호 해시 | 포맷·Salt 16바이트·해시 256비트, 같은 비밀번호도 해시가 다름, NFC·NFD 동일 처리, Trim 없음, 다른 pepper·키 ID·잘못된 포맷은 불일치, 반복 횟수 변경 시 재해시 대상. DB에 원문 없음 | M-02, MEM-04 | MB-D006 | `Pbkdf2PasswordHasherTest`, `SignupApiIntegrationTest` |
| MB-T008 | pepper 키 기동 검증 | 키 누락·키 ID 누락·256비트 아님·Base64 아님이면 기동 실패, 오류 메시지에 키 값 없음, 기본 반복 횟수 600,000 | M-02 | MB-D007 | `MemberPasswordPropertiesTest` |
| MB-T009 | ~~차단 목록 로딩~~ | 폐지: 로컬 유출 목록을 두지 않는다(MB-D010). 유출 검사는 MB-T012~MB-T017 | — | MB-D010 | — |
| MB-T010 | 스키마 | Flyway 마이그레이션 적용 + Hibernate 검증 통과, LOCAL 이메일 부분 유니크, CHECK(LOCAL 자격 증명·계정 상태), OAuth 연결 UNIQUE, 그 밖의 제약 위반은 409로 바꾸지 않음, 생성·수정 시각 자동 기록 | 3장 | MB-D008, G-D010 | `MemberRepositoryAdapterTest` |
| MB-T011 | Swagger Spec 일치 | `/v3/api-docs`의 회원가입: 태그 Authentication, 201·400·409·503(오류는 ProblemDetail 참조), 요청·응답 필드 | 4.6, MEM-22 | G-D008 | `SignupOpenApiIntegrationTest` |
| MB-T012 | HIBP 요청 범위 | `password` → `GET /range/5BAA6`(SHA-1 앞 5자만), `Add-Padding: true`·User-Agent 전송. 경로·헤더에 원문·전체 해시 없음 | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T013 | HIBP 응답 판정 | 일치 접미사(대소문자 무시)의 건수 1 이상 → 유출, 건수 0(패딩)만 일치 → 통과, 일치 없음 → 통과 | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T014 | HIBP 정규화 | NFC·NFD 입력이 같은 앞 5자로 조회 | M-02 | MB-D004, MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T015 | HIBP 비정상 응답 | 200 빈 본문, 형식 오류 줄, 숫자가 아닌 건수, 길이가 틀린 접미사, 301(도달 가능한 Location이어도 따라가지 않음), 헤더 뒤 본문 정지(전체 상한 안에 종료), 503, 429, 읽기 시간 초과, 연결 실패, 잘못된 base-url(요청 생성 실패) → 모두 확인 불가(통과로 보지 않음) | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T016 | HIBP 정보 비노출 | MB-T015 각 경우 로그에 원문·전체 SHA-1·앞 5자·`/range/` 없음 | M-02, 5장 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T017 | 가입 API의 유출 검사 | 유출 → 400 `MEMBER_PASSWORD_BREACHED`, 확인 불가 → 503 `MEMBER_PASSWORD_CHECK_UNAVAILABLE`(둘 다 고정 detail, `errors[]` 없음), 두 경우 DB 0건·응답과 로그에 비밀번호 없음. 실제 HIBP 호출 없음(대역) | M-01, M-02 | MB-D010 | `SignupServiceTest`, `SignupApiIntegrationTest` |
| MB-T018 | 로그인 1차 인증 성공 | 200 `status=OTP_REQUIRED`·`otpExpiresAt`, `__Host-yhs_otp`(HttpOnly·Secure·SameSite=Strict·Path=/·Max-Age 600·Domain 없음), 인증 쿠키 없음, 4자리 OTP 메일 1통. username 조회 | 원문 미대조 | MB-D011, MB-D013, MB-D015 | `LoginApiIntegrationTest`, `LoginServiceTest`, `MemberRepositoryAdapterTest` |
| MB-T019 | 실패 응답 동일·상태별 처리 | 비밀번호 불일치·없는 username·GOOGLE 계정·PENDING 만료 → 같은 401 `AUTH_LOGIN_FAILED`(instance 외 본문 동일), 없는 username·GOOGLE은 Dummy Hash 비교, 비밀번호 일치 DISABLED → 403 `MEMBER_ACCOUNT_DISABLED`·메일 없음·카운터 유지, 틀린 비밀번호 DISABLED → 401 | 원문 미대조 | MB-D009, MB-D011 | `LoginServiceTest`, `LoginApiIntegrationTest` |
| MB-T020 | 로그인 실패 제한 | 9회 401, 10번째부터 429 `AUTH_LOGIN_TEMPORARILY_BLOCKED`+`Retry-After`, 차단 중 비밀번호 검증 안 함·차단 연장 없음, 카운터 마지막 실패+24시간, 1차 성공 시 초기화(검증 중 생긴 차단은 429), PENDING 만료는 실패로 누적, 없는 username 동일, 키에 username 원문 없음, 동시 30건에서 9건만 비차단·차단 1번, Redis 장애 503 | 원문 미대조 | MB-D017 | `RedisLoginAttemptLimiterTest`, `LoginServiceTest`, `LoginApiIntegrationTest` |
| MB-T021 | OTP 생성·저장 | SecureRandom 0~9999 → "0000"~"9999", 저장소에 원문 없이 HMAC(challengeId:version:code)만, OTP 5분·Challenge 10분 | 원문 미대조 | MB-D015 | `OtpServiceTest`, `RedisOtpChallengeStoreTest` |
| MB-T022 | OTP 성공 → 로그인 완료 | 200 `memberId`·`role`·`accessTokenExpiresAt`, `__Host-yhs_at`(Path=/·Max-Age 900)·`__Secure-yhs_rt`(Path=/api/v1/auth·Max-Age 604800) HttpOnly·Secure·Strict, Challenge 쿠키 삭제(Max-Age 0)·Challenge 소비·재사용 401, PENDING→ACTIVE·`email_verified=true`(조건부 UPDATE, 멱등), CSRF 쿠키 갱신, Access 쿠키로 보호 API 인증 | 원문 미대조 | MB-D003, MB-D011~D015, G-D014 | `LoginApiIntegrationTest`, `LoginCompletionServiceTest`, `MemberRepositoryAdapterTest` |
| MB-T023 | OTP 검증 실패 | 불일치 401 `AUTH_OTP_MISMATCH`, 5번째 불일치로 OTP 무효(이후 정답도 401 `AUTH_OTP_EXPIRED`, 시도 소진 안내 문구), OTP 5분 만료는 실패로 세지 않음, Challenge 절대 10분 만료·교체된 Challenge·쿠키 없음 → 401 `AUTH_OTP_CHALLENGE_INVALID`, 형식 오류 400, Challenge로 보호 API 인증 불가 | 원문 미대조 | MB-D015 | `RedisOtpChallengeStoreTest`, `OtpServiceTest`, `LoginApiIntegrationTest` |
| MB-T024 | OTP 재발송 | 60초 간격(새 로그인에도 적용), version+1·시도 수 초기화·이전 OTP 무효, 최근에 보냈거나 보내려 한 4자리 2개(숫자 지문)와 같은 숫자는 다시 뽑음(실패한 메일의 숫자도 피함), Challenge 만료 유지·OTP 만료 ≤ Challenge 만료, 오래된 스냅샷의 재발송은 경합 처리 | 원문 미대조 | MB-D015, R-12 | `RedisOtpChallengeStoreTest`, `OtpServiceTest`, `LoginApiIntegrationTest` |
| MB-T025 | 회원 단위 OTP 제한 | 발급(새 로그인+재발송) 시간당 5·24시간 20, 검증 실패 합산 24시간 10 → 429 `AUTH_OTP_RATE_LIMITED`+`Retry-After`, 새 Challenge로 카운터 초기화 안 됨, 같은 회원 동시 로그인 10건 → 1건만 발급(나머지 429), 재발송과 경합한 검증은 실패로 세지 않음 | 원문 미대조 | MB-D017 | `RedisOtpChallengeStoreTest`, `OtpServiceTest`, `LoginApiIntegrationTest` |
| MB-T026 | 메일 발송 실패 | 로그인 중 실패 → 503 `MEMBER_OTP_DELIVERY_UNAVAILABLE`·Challenge 삭제·쿠키 없음, 재발송 중 실패 → 그 version OTP만 무효·Challenge 유지, 오래된 version의 보상은 새 OTP·Challenge를 건드리지 않음, 카운터·60초 유지, 발송 성공이 확인되기 전 OTP는 검증 불가(실패로 세지 않음, SMTP 타임아웃 + 보상 실패 + Redis 복구 뒤에도 통과 안 함), 오래된 version의 확인은 새 OTP를 활성화하지 않음, 보상이 저장소 장애로 실패해도 503 `MEMBER_OTP_DELIVERY_UNAVAILABLE`, 재발송 경합의 `Retry-After`는 설정 간격, SMTP 정상 발송(Mailpit)·연결 거부·응답 없는 서버 타임아웃, 예외·로그에 메일 주소·OTP·SMTP 오류 원문 없음 | 원문 미대조 | MB-D016 | `OtpServiceTest`, `RedisOtpChallengeStoreTest`, `SmtpOtpMailSenderTest`, `LoginApiIntegrationTest` |
| MB-T027 | 세션·Refresh 저장 | Session ID·Refresh 각각 256비트 Base64url 43자, Redis에 Refresh SHA-256만, 세션·Refresh 같은 7일 절대 만료(PEXPIREAT), Lua로 두 키 모두 없을 때만 저장(충돌 시 아무것도 쓰지 않음, 새 값으로 1회 재시도), 만료 판단은 주입한 `Clock`, 저장 실패·결과 미확인 → 503·쿠키 없음, `LoginSessionVerifier` ACTIVE·NOT_FOUND·MEMBER_MISMATCH·저장소 오류 | 원문 미대조 | MB-D014, G-D007 | `RedisLoginSessionStoreTest`, `LoginCompletionServiceTest`, `AccessTokenCookieIntegrationTest` |
| MB-T028 | IP 요청 제한 | 가입 IP당 시간당 5, 로그인·OTP IP당 10분 30 → 429 `COMMON_TOO_MANY_REQUESTS`+`Retry-After`, 창은 첫 요청부터(연장 없음), IP·범위별 분리, 키에 IP 원문 없음, `X-Forwarded-For` 무시, 동시 20건 중 5건만 허용, 저장소 장애 503 `COMMON_RATE_LIMIT_UNAVAILABLE` | 원문 미대조 | MB-D018 | `RedisRequestRateLimiterTest`, `LoginApiIntegrationTest` |
| MB-T029 | Redis 장애 fail-closed | Redis에 닿지 않으면 가입 503 `COMMON_RATE_LIMIT_UNAVAILABLE`, 로그인·OTP 503·인증 쿠키 없음, 유효한 Access Token도 세션 확인 불가로 503 `AUTH_SESSION_STORE_UNAVAILABLE` | 원문 미대조 | MB-D019, G-D007 | `RedisUnavailableIntegrationTest` |
| MB-T030 | 토큰 발급 전 상태 재확인 | OTP 대기 중 PENDING 만료 → 401 `AUTH_LOGIN_FAILED`·인증 쿠키·세션 없음·Challenge 쿠키 삭제·PENDING 유지, DISABLED → 403, 조건부 활성화 0행이면 재조회해 ACTIVE일 때만 계속, PENDING 만료 경계(가입 + 24시간 정각부터 만료) | 원문 미대조 | MB-D003, MB-D011 | `LoginCompletionServiceTest`, `LoginApiIntegrationTest`, `MemberTest`, `MemberRepositoryAdapterTest` |
| MB-T031 | 1차 인증 성공 시 재해시 | 비밀번호 일치·로그인 가능 상태 확인 뒤 `needsRehash`면 트랜잭션 밖 해시 + 기존 해시 조건 UPDATE(불일치면 0행), 실패해도 기존 해시 유지·로그인 계속·로그에 원문 없음, 실패·DISABLED 로그인에서는 재해시 없음 | M-02 | MB-D006 | `LoginServiceTest`, `MemberRepositoryAdapterTest`, `LoginApiIntegrationTest` |
| MB-T032 | Swagger Spec 일치(로그인·OTP) | `/v3/api-docs`의 login·otp/verify·otp/resend·csrf: 응답 코드·ProblemDetail 참조·`Retry-After` 헤더·Challenge 쿠키 파라미터·CSRF 보안 요구·DTO 필드, `HttpServletRequest` 숨김 | 4.6 | G-D008, MB-D013, G-D014 | `AuthOpenApiIntegrationTest` |
| MB-T033 | OTP 키·발신 주소 기동 검증 | OTP HMAC 키 누락·키 ID 누락·Base64 아님·256비트 아님, 발신 주소 누락·형식 오류 → 기동 실패, 메시지·`toString`에 키 값 없음 | — | MB-D007, MB-D016 | `MemberOtpPropertiesTest` |
| MB-T034 | 비밀 값 비노출 | 로그인~OTP 성공 흐름의 응답 본문·로그에 비밀번호·OTP·Access·Refresh·Challenge 값 없음(토큰은 쿠키로만) | 5장 | MB-D014~D016 | `LoginApiIntegrationTest` |

## 2. 집계

- 요구사항 34건(MB-T001~MB-T034, MB-T009 폐지). JUnit 테스트: member 157건(단위 95, 영속성 11, Redis 48, SMTP 3), app 37건(`SignupApiIntegrationTest` 13, `SignupOpenApiIntegrationTest` 1, `LoginApiIntegrationTest` 18, `RedisUnavailableIntegrationTest` 3, `AuthOpenApiIntegrationTest` 2). 실행 결과는 [regression-log.md](regression-log.md).
- "명세" 열 "원문 미대조": BE-03 정책은 기존 Member 명세(사용자 재확인)에서 왔지만 원문이 저장소에 없어 MEM 번호(MEM-05~MEM-08 등)와 대조하지 못했다.
- 수동 확인(자동 테스트 아님, 미실행): 개발 브라우저(http://localhost)에서 `__Host-`·Secure 쿠키가 저장·전송되는지(MB-D013).
- 아직 없는 명세 테스트: Refresh·로그아웃·Google(BE-04~BE-05).
