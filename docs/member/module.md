# member — module

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. To-Be 설계

- 계층 패키지(`in`·`app`·`domain`·`out`)를 쓴다([backend/README.md 5장](../../backend/README.md#5-모듈과-패키지-규칙)). `app`은 포트(`MemberRepository`)로만 `out`을 쓴다.
- 회원가입(MB-W01~W04)은 2장 As-Is와 같다. Refresh·로그아웃(MB-W09~W10)은 BE-04, Google(MB-W11)은 BE-05에서 설계한다.
- 로그인·OTP·토큰(MB-W05~W08, Issue #13): 1차 인증 → OTP Challenge·이메일 OTP → 로그인 세션·토큰 쿠키 발급. 정책은 [MB-D011](decisions.md#1-확정-결정)~[MB-D019](decisions.md#1-확정-결정), CSRF는 [G-D014](../policy.md#3-공통-결정-이력). 클래스·Redis 키·설정 설계는 구현 계획에서 정하고 구현 뒤 2장 As-Is에 적는다.
- `LoginSessionVerifier`(global 포트, G-D006·G-D007)는 member가 Redis 로그인 세션으로 구현한다.
- 비밀번호 재해시: `Pbkdf2PasswordHasher.needsRehash()`가 반복 횟수·키 ID 변경을 판정한다. 실제 재해시는 로그인 성공 시점(BE-03)에 한다([MB-D006](decisions.md#1-확정-결정)).
- OpenAPI cookie 보안 스킴(`__Host-yhs_at`, [MB-D013](decisions.md#1-확정-결정))은 MB-W05에서 정의한다([docs/global/module.md](../global/module.md) 1장).

## 2. As-Is

2026-11-26, 브랜치 `feature/13-member-login-otp` 기준(회원가입은 #10, 로그인·OTP·토큰은 #13).

- 빌드: [modules/member/build.gradle.kts](../../backend/modules/member/build.gradle.kts) — global, springdoc(Spec 어노테이션), `spring-boot-starter-data-redis`, `spring-boot-starter-mail`. 테스트: data-jpa-test, data-redis-test, Testcontainers PostgreSQL·Redis(GenericContainer)·Mailpit·JUnit, Flyway, PostgreSQL 드라이버.
- 패키지와 클래스

| 패키지 | 클래스 | 내용 |
| --- | --- | --- |
| `domain` | `Member`, `AccountType`, `AccountStatus`, `MemberRole` | `Member.registerLocal()`은 LOCAL·PENDING·이메일 미확인·USER로 만든다([MB-D003](decisions.md#1-확정-결정)) |
| `app` | `SignupService`, `SignupCommand`, `SignupResult`, `MemberRepository`(포트), `MemberErrorCode` | 사전 중복 검사 → 유출 비밀번호 검사 → 비밀번호 해시 → 저장. HIBP 호출과 해시는 트랜잭션 밖. 중복은 409(`MEMBER_USERNAME_DUPLICATE`·`MEMBER_EMAIL_DUPLICATE`), 유출은 400 `MEMBER_PASSWORD_BREACHED`, 확인 불가는 503 `MEMBER_PASSWORD_CHECK_UNAVAILABLE`([MB-D010](decisions.md#1-확정-결정)) |
| `app.password` | `MemberPasswordProperties`, `MemberPasswordConfig`, `PasswordHasher`(계약)·`Pbkdf2PasswordHasher`, `PasswordNormalization`(NFC 한 곳), `PasswordPolicy`, `PasswordViolation`, `BreachedPasswordChecker`(포트) | 해시·정책(길이·문맥 단어)·유출 검사 계약([MB-D004~D007](decisions.md#1-확정-결정), [MB-D010](decisions.md#1-확정-결정)) |
| `app.login` | `LoginService`, `LoginCompletionService`, `CompletedLogin`, `MemberLoginConfig` | 1차 인증, OTP 성공 뒤 상태 재확인·활성화·세션 발급(아래 "로그인·OTP") |
| `app.otp` | `OtpService`, `LoginChallenge`, `OtpChallengeStarter`, `OtpCodes`, `MemberOtpProperties`, `OtpChallengeStore`(포트), `OtpMailSender`(포트), `MemberLookup`, `NewOtpChallenge`, `OtpChallengeSnapshot`, `OtpStoreResult`, `OtpResent` | OTP 발급·재발송·검증, 발송 실패 보상([MB-D015](decisions.md#1-확정-결정)·[MB-D016](decisions.md#1-확정-결정)) |
| `app.limit` | `MemberLimitProperties`, `RequestRateLimiter`(포트), `LoginAttemptLimiter`(포트), `RateLimitScope` | 제한 수치·포트([MB-D017](decisions.md#1-확정-결정)·[MB-D018](decisions.md#1-확정-결정)) |
| `app.session` | `LoginSessionStore`(포트), `NewLoginSession`, `RandomTokens`, `MemberAccessTokenRoles` | 세션·Refresh 저장 계약, 256비트 난수·SHA-256, JWT `role` 허용 값 |
| `app.password` | `DummyPasswordHash` | 없는 username·GOOGLE 로그인의 같은 비용 비교 |
| `out` | `MemberJpaRepository`, `MemberRepositoryAdapter`, `HibpPwnedPasswordsClient`, `HibpProperties`, `HibpClientConfig` | HIBP: 아래 "유출 비밀번호 검사". 저장: `saveAndFlush`로 즉시 INSERT하고, `uk_members_username`·`ux_members_local_email` 위반만 409로 바꾼다. 그 밖의 제약 위반은 제약 이름·SQLState만 WARN으로 남기고 원인 없는 `IllegalStateException`(500)으로 던진다(DB 상세에 든 개인정보를 응답·로그에 싣지 않으려고). 로그인용 `findByUsername`·`findById`, 조건부 UPDATE 2개(재해시: 기존 해시 조건, 활성화: PENDING·`created_at > now-24h` 조건, 둘 다 짧은 트랜잭션 하나) |
| `out.redis` | `RedisLoginSessionStore`(+`LoginSessionVerifier` 구현), `RedisOtpChallengeStore`, `RedisLoginAttemptLimiter`, `RedisRequestRateLimiter`, `RedisLuaScripts`, `RedisKeys` | 아래 "Redis 키·Lua". Redis 오류는 인증 흐름 503 `AUTH_SESSION_STORE_UNAVAILABLE`, 요청 제한 503 `COMMON_RATE_LIMIT_UNAVAILABLE` |
| `out.mail` | `SmtpOtpMailSender`, `MemberOtpMailProperties`, `MemberMailConfig`, `OtpDeliveryException` | SMTP 동기 발송, 실패는 원인·주소·OTP 없는 예외 |
| `in` | `ApiV1AuthController`, `ApiV1AuthSpec`, `AuthCookieWriter`, `MemberAuthCookies`(Refresh·Challenge 쿠키 이름·Refresh Path), `ClientIp`, `dto/SignupRequest`·`SignupResponse`·`LoginRequest`·`OtpVerifyRequest`·`OtpRequiredResponse`·`LoginSuccessResponse`, `ValidSignupPassword`, `SignupPasswordValidator`, `MemberPublicEndpoints` | 가입·로그인·OTP API. IP 제한을 서비스보다 먼저 건다. 쿠키 발급·삭제는 `AuthCookieWriter` 한 곳(HttpOnly·Secure·Strict·Domain 없음, Max-Age = 만료까지 남은 초 올림). 공개 경로: signup·login·otp/verify·otp/resend |

- 비밀번호 해시 포맷: `pbkdf2-sha256$v1$nfc$i=<반복 횟수>$k=<키 ID>$<Salt Base64>$<해시 Base64>`. Salt 16바이트(SecureRandom), PBKDF2 출력 256비트, 최종 HMAC-SHA256(pepper) 256비트, Base64는 패딩 없음. 검증은 `MessageDigest.isEqual`(일정 시간). 포맷 오류·키 ID 불일치는 불일치로 처리한다.
- 비밀번호 정책: NFC 뒤 code point 15~64자. 문맥 단어 판정은 "소문자 → 글자·숫자만 → 끝 숫자 제거"한 핵심어가 username·서비스 이름(`yhslivestreaming`·`yhslive`·`livestreaming`·`yhs`)의 핵심어와 같으면(뒤집은 경우 포함) 거부한다. 끝 숫자는 code point 단위로 뗀다. 또 글자·숫자만 남긴 값(끝 숫자를 떼기 전)이 username과 같거나 뒤집어 같으면 거부한다(숫자·밑줄만인 username 대비). 대소문자는 구분하지 않는다.
- 설정: `member.password.pepper-key`·`pepper-key-id`(환경변수 `MEMBER_PASSWORD_PEPPER_KEY`·`_KEY_ID`, 기본값 없음), `iterations`(기본 600,000), `member.password.hibp.base-url`·`connect-timeout`·`read-timeout`(기본값은 `HibpProperties`, 정책 수치는 MB-D010). 키가 없거나 Base64가 아니거나 256비트가 아니면 기동에 실패한다.
- 입력 정규화: username은 ASCII(`[A-Za-z0-9_]`)일 때만 소문자로 바꾼다(비ASCII가 소문자 변환으로 ASCII가 되는 우회 방지). 닉네임은 앞뒤 공백을 떼고 code point 2~20자, Unicode Cc·Cf(제어·서식 문자) 금지. 이메일은 앞뒤 공백 제거·소문자.
- 로그: `logging.level.org.hibernate.orm.jdbc.error: OFF`(app `application.yml`). Hibernate 7이 SQL 오류 상세(`Key (email)=(...)`)를 남기는 카테고리다. 예외는 그대로 전파된다.
- 비밀번호 원문 보호: `SignupRequest`·`SignupCommand`·`MemberPasswordProperties`의 `toString()`은 비밀번호·키를 가린다. 오류 응답은 필드와 고정 문구만 내보낸다.
- 테스트: 단위(인프라 없음, HIBP는 JDK `HttpServer` 스텁, 로그인·OTP 서비스는 대역), 영속성(Testcontainers PostgreSQL + Flyway, `@DataJpaTest`), Redis 어댑터(Testcontainers Redis 싱글턴 + `@DataRedisTest`), SMTP 어댑터(Testcontainers Mailpit, 연결 거부·응답 없는 소켓), app 통합(`SignupApiIntegrationTest`, `SignupOpenApiIntegrationTest`, `LoginApiIntegrationTest`, `RedisUnavailableIntegrationTest`, `AuthOpenApiIntegrationTest`. HIBP는 `StubBreachedPasswordChecker`, 메일은 `CapturingOtpMailSender` 대역). 실제 HIBP·Gmail은 부르지 않는다. 건수는 [test-spec.md](test-spec.md) 2장.

### 로그인·OTP (BE-03)

- 결정: [MB-D011](decisions.md#1-확정-결정)~[MB-D019](decisions.md#1-확정-결정), CSRF [G-D014](../policy.md#3-공통-결정-이력). 트랜잭션은 두지 않는다(해시·Redis·메일 동안 DB 연결을 잡지 않음). DB 쓰기는 조건부 UPDATE 2개뿐이다.
- 1차 인증(`LoginService`): 차단 확인(차단 중이면 비밀번호 검증 없음) → username 조회(LOCAL·해시 있음만) → 비밀번호 비교(없으면 `DummyPasswordHash`) → 불일치·PENDING 만료면 실패 기록(10번째부터 429) → DISABLED 403(초기화·OTP 없음) → 실패 카운터 초기화(그 사이 차단이 생겼으면 429) → 재해시 → OTP 시작.
- 재해시(MB-D006 세부): 비밀번호 원문은 1차 인증 요청 안에만 있으므로 1차 인증 성공(일치 + 로그인 가능 상태 확인) 뒤에 한다. `needsRehash`일 때 트랜잭션 밖에서 해시하고 기존 해시를 조건으로 UPDATE한다. 실패하면 기존 해시를 두고 로그인을 계속하며 회원 ID·예외 종류만 WARN으로 남긴다.
- OTP(`OtpService`): 4자리, HMAC(`challengeId:version:code`, OTP 키)만 저장. 새 로그인은 `otp_issue.lua`(60초·시간당 5·24시간 20·실패 합산 10 검사 → 카운터 증가 → 이전 Challenge 삭제 → 새 Challenge·포인터). 재발송은 현재 version을 읽어 version+1 HMAC을 만들고 `otp_resend.lua`가 같은 version일 때만 반영한다(최근에 보냈거나 보내려 한 4자리 지문 최대 2개(`sentDigest`·`prevSentDigest`)와 같은 숫자는 다시 뽑음. 지문은 version에 묶이지 않은 HMAC(`challengeId:code`)이고 검증에는 쓰지 않으며, 실패한 메일도 전달됐을 수 있어 지우지 않는다). 검증은 현재 version으로 HMAC을 만들어 `otp_verify.lua`가 같은 version일 때만 비교한다(다르면 실패로 세지 않고 401 `AUTH_OTP_EXPIRED` "새 인증 번호 발송" 안내).
- 발송 성공 확인: 새로 저장한 OTP는 `confirmed=0`이라 검증할 수 없고, SMTP 발송이 성공한 뒤 `otp_confirm_delivered.lua`가 같은 version일 때만 `confirmed=1`로 바꾼다. 확인 반영이 안 되면(version 변경) 503, Redis 장애면 503이며 그 OTP는 끝까지 쓸 수 없다. 그래서 SMTP 타임아웃(실제로는 전달됐을 수 있음)과 보상 실패가 겹쳐도 발송 결과를 모르는 OTP로 로그인할 수 없다.
- 메일 발송 실패: 저장 뒤 동기 발송이 실패하면 `otp_delivery_failed.lua`가 같은 version일 때만 보상한다(로그인: Challenge·포인터 삭제, 재발송: 그 OTP 무효). 카운터·60초는 되돌리지 않는다. 보상이 저장소 장애로 실패해도 응답은 503 `MEMBER_OTP_DELIVERY_UNAVAILABLE`이다(남는 상태: 쿠키 없는 Challenge 또는 받지 못한 OTP, 둘 다 쓸 수 없음). 재발송 경합에서 진 요청의 `Retry-After`는 설정한 발급 간격이다.
- 로그인 완료(`LoginCompletionService`): Challenge 소비 뒤 회원 재조회(없음·PENDING 만료 401, DISABLED 403) → PENDING이면 조건부 활성화(0행이면 재조회해 ACTIVE일 때만 계속) → Session ID·Refresh 생성 → Access 서명 → `session_create.lua`(충돌이면 새 값으로 1회 더) → 쿠키·CSRF 갱신. Challenge 쿠키는 OTP 확인 직후(완료 처리 전) 지운다. 그래서 완료 처리가 401·403·503으로 끝나도 쓸모없는 Challenge 쿠키가 남지 않는다. Redis 어댑터의 만료 판단은 주입한 `Clock`을 쓴다. 중간 실패 때 남는 상태: Challenge 소비됨(다시 로그인), 활성화는 OTP로 이메일 소유가 확인된 뒤라 유지, 응답 전송 실패 시 세션은 클라이언트가 모르는 채 7일 TTL로 사라짐.
- 오류 코드: `AUTH_LOGIN_FAILED`(401), `AUTH_LOGIN_TEMPORARILY_BLOCKED`(429), `MEMBER_ACCOUNT_DISABLED`(403), `AUTH_OTP_MISMATCH`·`AUTH_OTP_EXPIRED`·`AUTH_OTP_CHALLENGE_INVALID`(401), `AUTH_OTP_RATE_LIMITED`(429), `MEMBER_OTP_DELIVERY_UNAVAILABLE`(503). 429는 모두 `Retry-After`.
- 클라이언트 IP: `getRemoteAddr()`만(MB-D018, MB-U06).
- 설정: `member.otp.hmac-key`·`hmac-key-id`(환경변수 `MEMBER_OTP_HMAC_KEY`·`_ID`, 기본값 없음), `member.otp.mail.from`(`MEMBER_OTP_MAIL_FROM`), `member.limits.*`(기본값 = 정책 값), `spring.mail.*`(호스트·계정은 환경변수, SMTP 인증·STARTTLS 플래그는 기본값 없는 `MEMBER_SMTP_AUTH`·`MEMBER_SMTP_STARTTLS`). 메일 health는 끈다(app `application.yml`).

### Redis 키·Lua

username·IP는 SHA-256 hex로 키에 쓴다. 원문이 키 목록에 남지 않게 하려는 것이며 입력 공간이 작아 사전 대입으로 되돌릴 수 있으므로 익명화로 보지 않는다. `otp_issue.lua`는 이전 Challenge 키를 포인터에서 읽어 지우므로 단일 Redis(클러스터 아님)를 전제한다.

| 키 | 자료형 | 내용 | TTL |
| --- | --- | --- | --- |
| `auth:session:{sid}` | HASH | memberId, refreshHash, expiresAt(ms) | 절대 만료(PEXPIREAT, 7일) |
| `auth:refresh:{sha256(refresh)}` | HASH | sid, memberId, expiresAt | 같은 절대 만료 |
| `auth:otp:challenge:{challengeId}` | HASH | memberId, version, otpHmac(무효면 빈 값), keyId, attempts, otpExpiresAt, challengeExpiresAt, confirmed(발송 성공 확인 0/1), sentDigest·prevSentDigest(최근 숫자 지문 2개) | Challenge 절대 10분 |
| `auth:otp:member:{id}:current` | STRING | 현재 challengeId | Challenge 만료까지 |
| `auth:otp:member:{id}:last-issued` | STRING | 60초 발급 간격 | 60초 |
| `auth:otp:member:{id}:issue:1h`·`issue:24h` | 카운터 | 발급 수(새 로그인 + 재발송) | 첫 증가부터 1시간·24시간 |
| `auth:otp:member:{id}:fail:24h` | 카운터 | OTP 검증 실패 합산 | 첫 증가부터 24시간 |
| `auth:login:fail:{sha256(username)}` | 카운터 | 연속 실패 | 마지막 실패 + 24시간 |
| `auth:login:block:{sha256(username)}` | STRING | 차단 | 15분(연장 없음) |
| `ratelimit:{signup\|login\|otp}:{sha256(ip)}` | 카운터 | IP 요청 수 | 첫 요청부터 1시간·10분·10분 |

| Lua(`src/main/resources/redis/member`) | 원자 처리 범위 |
| --- | --- |
| `session_create` | 두 키가 모두 없을 때만 HSET + PEXPIREAT 2쌍. 모든 확인을 쓰기 전에 끝낸다. 결과를 확인하지 못하면(예외·null) 인증 쿠키를 주지 않는다 |
| `rate_limit` | INCR → 첫 값이면 PEXPIRE, TTL 없는 키 보정, 초과면 남은 ms |
| `login_fail` / `login_success` | 차단 중이면 세지 않음 / 차단 키 생성 + 카운터 삭제 / 성공 시 차단 확인 뒤 카운터 삭제 |
| `otp_issue` / `otp_resend` / `otp_verify` / `otp_confirm_delivered` / `otp_delivery_failed` | 위 "로그인·OTP" 참조. 새 OTP는 발송 확인 전 검증 불가 |

### 유출 비밀번호 검사 (HIBP)

- 결정: [MB-D010](decisions.md#1-확정-결정). 로컬 유출 목록은 두지 않는다(SecLists xato-net·Probable-Wordlists는 재배포 권한을 확인하지 못했다, 2026-11-26 조사).
- 구현: `HibpPwnedPasswordsClient`가 JDK `java.net.http.HttpClient`로 MB-D010의 요청·판정·로그 규칙을 따른다. 요청 경로는 `{base-url}/range/{앞 5자}`, 고정 User-Agent `yhs-live-streaming-backend`, 리다이렉트는 따르지 않는다(3xx도 확인 불가).
- 시간 제한 구현: 연결 제한은 `HttpClient.connectTimeout`, 응답 제한은 `HttpRequest.timeout`(응답 헤더까지), 본문까지 포함한 전체 상한은 연결 + 응답이다.
- 요청 생성 실패(잘못된 `base-url` 등)도 확인 불가로 처리하고 예외를 밖으로 내보내지 않는다(예외 메시지에 URL이 들어갈 수 있다).
- Spring `RestClient`를 쓰지 않는 이유: I/O 예외 메시지에 요청 URL(앞 5자 포함)이 들어간다. JDK HttpClient 내부 로그(`jdk.httpclient.HttpClient.log`, `jdk.internal.httpclient.debug`)는 요청 URI를 출력하므로 운영에서 켜지 않는다.
- 운영 조건: 서버에서 `api.pwnedpasswords.com:443`으로 나가는 통신이 허용돼야 한다. 막히면 가입이 503이다.

## 3. 공개 API

- 다른 모듈에 줄 공개 API(회원 조회 등)는 아직 없다(MB-U 후보: broadcast 착수 전).

## 4. 다른 모듈 의존

- global: `ErrorCode`·`BusinessException`·`TooManyRequestsException`(오류), `PublicEndpointContributor`(공개 경로), `BaseTimeEntity`·`GlobalJpaConfig`(JPA 공통), OpenAPI `ProblemDetail` 스키마, `AccessTokenIssuer`·`AccessTokenRoles`(JWT), `LoginSessionVerifier`·`LoginSessionStoreUnavailableException`·`AuthCookieNames`(인증 계약), `CsrfTokenRenewer`(CSRF 갱신), `Clock`.

## 5. 데이터 모델

- 원본: Flyway `modules/member/src/main/resources/db/migration/V20261126_1__member_create_members.sql`. 버전 번호는 날짜형(`V<yyyymmdd>_<순번>`)으로 모듈 사이 충돌을 피한다.

| 테이블 | 컬럼(타입, NULL) | 제약 |
| --- | --- | --- |
| `members` | `id` BIGINT IDENTITY PK, `username` VARCHAR(50) NULL, `password_hash` VARCHAR(255) NULL, `email` VARCHAR(255) NOT NULL, `email_verified` BOOLEAN NOT NULL, `nickname` VARCHAR(50) NOT NULL, `role` VARCHAR(20) NOT NULL, `account_type` VARCHAR(20) NOT NULL, `account_status` VARCHAR(20) NOT NULL, `created_at`·`updated_at` TIMESTAMPTZ NOT NULL | UNIQUE `uk_members_username`(username). CHECK `ck_members_account_type`(LOCAL·GOOGLE), `ck_members_account_status`(PENDING·ACTIVE·DISABLED), `ck_members_local_credentials`(LOCAL이면 username·password_hash 필수). 부분 유니크 인덱스 `ux_members_local_email`(email, `account_type='LOCAL'`) |
| `member_oauth_accounts` | `id` BIGINT IDENTITY PK, `member_id` BIGINT NOT NULL, `provider` VARCHAR(30) NOT NULL, `provider_user_id` VARCHAR(255) NOT NULL, `email` VARCHAR(255) NULL, `created_at` TIMESTAMPTZ NOT NULL | FK `fk_member_oauth_accounts_member`, UNIQUE `uk_member_oauth_accounts_provider_user`(provider, provider_user_id) |

- 명세와 다른 점: 명세 3.2는 `member_oauth_accounts.member_id`의 NULL 허용 여부를 적지 않았다. 연결 행은 반드시 회원에 속하므로 NOT NULL로 두었다. 엔티티(`OAuthAccount`)는 BE-05에서 만든다.

## 6. REST API

| Method | 경로 | 접근 | 성공 | 오류 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/signup` | 공개(CSRF) | 201 `SignupResponse(memberId, accountStatus=PENDING)`, 토큰 없음 | 400 `COMMON_INVALID_INPUT` + `errors[]`, 400 `MEMBER_PASSWORD_BREACHED`(`errors[]` 없음), 403 `AUTH_CSRF_INVALID`, 409 `MEMBER_USERNAME_DUPLICATE`·`MEMBER_EMAIL_DUPLICATE`, 429 `COMMON_TOO_MANY_REQUESTS`, 503 `MEMBER_PASSWORD_CHECK_UNAVAILABLE`(`errors[]` 없음, [MB-D010](decisions.md#1-확정-결정))·`COMMON_RATE_LIMIT_UNAVAILABLE` |
| POST | `/api/v1/auth/login` | 공개(CSRF) | 200 `OtpRequiredResponse(status=OTP_REQUIRED, otpExpiresAt)` + `__Host-yhs_otp` | 400 `COMMON_INVALID_INPUT`, 401 `AUTH_LOGIN_FAILED`, 403 `MEMBER_ACCOUNT_DISABLED`·`AUTH_CSRF_INVALID`, 429 `AUTH_LOGIN_TEMPORARILY_BLOCKED`·`AUTH_OTP_RATE_LIMITED`·`COMMON_TOO_MANY_REQUESTS`, 503 `MEMBER_OTP_DELIVERY_UNAVAILABLE`·`AUTH_SESSION_STORE_UNAVAILABLE`·`COMMON_RATE_LIMIT_UNAVAILABLE` |
| POST | `/api/v1/auth/otp/resend` | 공개(CSRF, Challenge 쿠키) | 200 `OtpRequiredResponse` | 401 `AUTH_OTP_CHALLENGE_INVALID`, 403 `AUTH_CSRF_INVALID`, 429 `AUTH_OTP_RATE_LIMITED`·`COMMON_TOO_MANY_REQUESTS`, 503 |
| POST | `/api/v1/auth/otp/verify` | 공개(CSRF, Challenge 쿠키) | 200 `LoginSuccessResponse(memberId, role, accessTokenExpiresAt)` + `__Host-yhs_at`·`__Secure-yhs_rt`·새 `XSRF-TOKEN`, Challenge 쿠키 삭제 | 400 `COMMON_INVALID_INPUT`, 401 `AUTH_OTP_MISMATCH`·`AUTH_OTP_EXPIRED`·`AUTH_OTP_CHALLENGE_INVALID`·`AUTH_LOGIN_FAILED`, 403 `MEMBER_ACCOUNT_DISABLED`·`AUTH_CSRF_INVALID`, 429 `AUTH_OTP_RATE_LIMITED`·`COMMON_TOO_MANY_REQUESTS`, 503(인증 쿠키 없음) |

- `GET /api/v1/auth/csrf`(CSRF 토큰 초기 발급)는 global이 제공한다([docs/global/module.md](../global/module.md)).
- 요청·응답·오류 스키마의 원본은 `ApiV1AuthSpec`(Swagger)이고, 실제 응답과의 일치는 `SignupOpenApiIntegrationTest`·`AuthOpenApiIntegrationTest`·`SignupApiIntegrationTest`·`LoginApiIntegrationTest`로 확인한다.
