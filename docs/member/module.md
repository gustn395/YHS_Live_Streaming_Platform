# member — module

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. To-Be 설계

- 계층 패키지(`in`·`app`·`domain`·`out`)를 쓴다([backend/README.md 5장](../../backend/README.md#5-모듈과-패키지-규칙)). `app`은 포트(`MemberRepository`)로만 `out`을 쓴다.
- 회원가입(MB-W01~W04)은 2장 As-Is와 같다. 로그인·OTP·토큰(MB-W05~W08)은 BE-03, Refresh·로그아웃(MB-W09~W10)은 BE-04, Google(MB-W11)은 BE-05에서 설계한다.
- 비밀번호 재해시: `Pbkdf2PasswordHasher.needsRehash()`가 반복 횟수·키 ID 변경을 판정한다. 실제 재해시는 로그인 성공 시점(BE-03)에 한다([MB-D006](decisions.md#1-확정-결정)).
- OpenAPI cookie 보안 스킴은 MB-W05에서 정의한다([docs/global/module.md](../global/module.md) 1장).

## 2. As-Is

2026-11-26, 브랜치 `feature/10-member-signup` 기준.

- 빌드: [modules/member/build.gradle.kts](../../backend/modules/member/build.gradle.kts) — global, springdoc(Spec 어노테이션). 테스트: data-jpa-test, Testcontainers PostgreSQL·JUnit, Flyway, PostgreSQL 드라이버.
- 패키지와 클래스

| 패키지 | 클래스 | 내용 |
| --- | --- | --- |
| `domain` | `Member`, `AccountType`, `AccountStatus`, `MemberRole` | `Member.registerLocal()`은 LOCAL·PENDING·이메일 미확인·USER로 만든다([MB-D003](decisions.md#1-확정-결정)) |
| `app` | `SignupService`, `SignupCommand`, `SignupResult`, `MemberRepository`(포트), `MemberErrorCode` | 사전 중복 검사 → 유출 비밀번호 검사 → 비밀번호 해시 → 저장. HIBP 호출과 해시는 트랜잭션 밖. 중복은 409(`MEMBER_USERNAME_DUPLICATE`·`MEMBER_EMAIL_DUPLICATE`), 유출은 400 `MEMBER_PASSWORD_BREACHED`, 확인 불가는 503 `MEMBER_PASSWORD_CHECK_UNAVAILABLE`([MB-D010](decisions.md#1-확정-결정)) |
| `app.password` | `MemberPasswordProperties`, `MemberPasswordConfig`, `PasswordHasher`(계약)·`Pbkdf2PasswordHasher`, `PasswordNormalization`(NFC 한 곳), `PasswordPolicy`, `PasswordViolation`, `BreachedPasswordChecker`(포트) | 해시·정책(길이·문맥 단어)·유출 검사 계약([MB-D004~D007](decisions.md#1-확정-결정), [MB-D010](decisions.md#1-확정-결정)) |
| `out` | `MemberJpaRepository`, `MemberRepositoryAdapter`, `HibpPwnedPasswordsClient`, `HibpProperties`, `HibpClientConfig` | HIBP: 아래 "유출 비밀번호 검사". 저장: `saveAndFlush`로 즉시 INSERT하고, `uk_members_username`·`ux_members_local_email` 위반만 409로 바꾼다. 그 밖의 제약 위반은 제약 이름·SQLState만 WARN으로 남기고 원인 없는 `IllegalStateException`(500)으로 던진다(DB 상세에 든 개인정보를 응답·로그에 싣지 않으려고) |
| `in` | `ApiV1AuthController`, `ApiV1AuthSpec`, `dto/SignupRequest`, `dto/SignupResponse`, `ValidSignupPassword`, `SignupPasswordValidator`, `MemberPublicEndpoints` | 회원가입 API. 검증 실패는 400 `errors[]`, 공개 경로 등록 |

- 비밀번호 해시 포맷: `pbkdf2-sha256$v1$nfc$i=<반복 횟수>$k=<키 ID>$<Salt Base64>$<해시 Base64>`. Salt 16바이트(SecureRandom), PBKDF2 출력 256비트, 최종 HMAC-SHA256(pepper) 256비트, Base64는 패딩 없음. 검증은 `MessageDigest.isEqual`(일정 시간). 포맷 오류·키 ID 불일치는 불일치로 처리한다.
- 비밀번호 정책: NFC 뒤 code point 15~64자. 문맥 단어 판정은 "소문자 → 글자·숫자만 → 끝 숫자 제거"한 핵심어가 username·서비스 이름(`yhslivestreaming`·`yhslive`·`livestreaming`·`yhs`)의 핵심어와 같으면(뒤집은 경우 포함) 거부한다.
- 설정: `member.password.pepper-key`·`pepper-key-id`(환경변수 `MEMBER_PASSWORD_PEPPER_KEY`·`_KEY_ID`, 기본값 없음), `iterations`(기본 600,000), `member.password.hibp.base-url`·`connect-timeout`·`read-timeout`(기본값은 `HibpProperties`, 정책 수치는 MB-D010). 키가 없거나 Base64가 아니거나 256비트가 아니면 기동에 실패한다.
- 입력 정규화: username은 ASCII(`[A-Za-z0-9_]`)일 때만 소문자로 바꾼다(비ASCII가 소문자 변환으로 ASCII가 되는 우회 방지). 닉네임은 앞뒤 공백을 떼고 code point 2~20자, Unicode Cc·Cf(제어·서식 문자) 금지. 이메일은 앞뒤 공백 제거·소문자.
- 로그: `logging.level.org.hibernate.orm.jdbc.error: OFF`(app `application.yml`). Hibernate 7이 SQL 오류 상세(`Key (email)=(...)`)를 남기는 카테고리다. 예외는 그대로 전파된다.
- 비밀번호 원문 보호: `SignupRequest`·`SignupCommand`·`MemberPasswordProperties`의 `toString()`은 비밀번호·키를 가린다. 오류 응답은 필드와 고정 문구만 내보낸다.
- 테스트: 단위 6클래스(인프라 없음, HIBP는 JDK `HttpServer` 스텁), 영속성 1클래스(Testcontainers PostgreSQL + Flyway, `@DataJpaTest`), app 통합 2클래스(`SignupApiIntegrationTest`, `SignupOpenApiIntegrationTest`, HIBP는 `testsupport.StubBreachedPasswordChecker` 대역). 실제 HIBP는 부르지 않는다. 건수는 [test-spec.md](test-spec.md) 2장.

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

- global: `ErrorCode`·`BusinessException`(오류), `PublicEndpointContributor`(공개 경로), `BaseTimeEntity`·`GlobalJpaConfig`(JPA 공통), OpenAPI `ProblemDetail` 스키마.

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
| POST | `/api/v1/auth/signup` | 공개 | 201 `SignupResponse(memberId, accountStatus=PENDING)`, 토큰 없음 | 400 `COMMON_INVALID_INPUT` + `errors[]`, 409 `MEMBER_USERNAME_DUPLICATE`·`MEMBER_EMAIL_DUPLICATE` |

- 요청·응답·오류 스키마의 원본은 `ApiV1AuthSpec`(Swagger)이고, 실제 응답과의 일치는 `SignupOpenApiIntegrationTest`·`SignupApiIntegrationTest`로 확인한다.
