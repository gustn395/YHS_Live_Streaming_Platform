# Backend (Spring Boot)

YHS Live Streaming Platform의 백엔드다. 지금은 **프로젝트 기본 구조, 빌드, 테스트, 실행 환경만** 있고 도메인 기능은 없다(Step 3).

| 항목 | 값 |
| --- | --- |
| Java | 17 (Gradle toolchain) |
| Spring Boot | 4.1.3 (Spring Framework 7.0.11, Jakarta EE 11 / Servlet 6.1) |
| Build | Gradle 9.9.2 wrapper, Kotlin DSL, 멀티 모듈 |
| Packaging | WAR (내장 Tomcat 11.0.27 실행 + 외부 Tomcat 11 배포) |
| 기본 패키지 | `com.yhs.livestreaming` |
| 상태 확인 | `GET /actuator/health` |

## 1. 준비: JDK

- **컴파일·테스트는 JDK 17**로 한다. Gradle toolchain이 설치된 JDK 17을 자동으로 찾는다(Windows 레지스트리, IntelliJ가 받은 `~/.jdks`, `JAVA_HOME` 등).
- **Gradle 실행**은 JDK 17 이상이면 된다(Gradle 9.9.2는 JDK 25로도 실행 가능). `JAVA_HOME`이 25여도 컴파일은 17로 된다.
- JDK 17 설치: [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17) 또는 IntelliJ의 *Project Structure → SDKs → Download JDK*.

```bash
./gradlew -q javaToolchains     # 감지된 JDK 목록에 "Language Version: 17"이 있어야 한다
```

JDK 17이 감지되지 않으면, 저장소가 아니라 **사용자 홈의** `~/.gradle/gradle.properties`에 경로를 적는다(사람마다 경로가 다르므로 저장소에 넣지 않는다).

```properties
org.gradle.java.installations.paths=C:/Program Files/Eclipse Adoptium/jdk-17.0.x-hotspot
```

## 2. 빌드·테스트·실행

모든 명령은 `backend/`에서 실행한다. Windows는 `gradlew.bat`(PowerShell은 `.\gradlew.bat`), Linux·macOS·Git Bash는 `./gradlew`.

| 작업 | 명령 | 결과 |
| --- | --- | --- |
| 테스트 | `./gradlew test` | 리포트: `app/build/reports/tests/test/index.html` |
| 전체 빌드 | `./gradlew build` | 테스트 + WAR 2개 (아래) |
| 개발 실행 | `./gradlew :app:bootRun` | http://localhost:8080 |
| 정리 | `./gradlew clean` | |

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

실행에는 PostgreSQL·Redis·SMTP 서버가 필요하다(접속 정보와 키는 4장 환경변수). 개발용 SMTP는 Mailpit을 쓴다(`docker run -p 1025:1025 -p 8025:8025 axllent/mailpit`, 받은 메일은 http://localhost:8025). 테스트는 Testcontainers로 PostgreSQL·Redis·Mailpit을 띄우므로 **Docker가 실행 중이어야 한다**([G-D009](../docs/policy.md#3-공통-결정-이력)). 단위 테스트는 외부 인프라 없이 돈다.

### CI (GitHub Actions)

main 대상 PR, main push, 수동 실행(`workflow_dispatch`) 때 [`.github/workflows/backend-ci.yml`](../.github/workflows/backend-ci.yml)이 실행된다. 변경 감지 Job(`backend-changes`)이 Backend 변경이 있다고 판정하면 `backend-build` Job이 Ubuntu + Temurin 17에서 위 `./gradlew clean build`를 그대로 실행하고, 최종 Gate Job `backend-ci`가 결과를 판정한다. main Ruleset의 필수 체크는 전환 절차를 거쳐 `backend-build`에서 Gate `backend-ci`·`frontend-ci`로 바뀐다(감지·Gate·전환 절차와 기록은 [`docs/testing/fe02-ci-split.md`](../docs/testing/fe02-ci-split.md)). 테스트 리포트(`test-reports`)는 실패해도 올라가고, 실행 가능한 WAR(`backend-war`)는 성공할 때만 올라간다. 정책·버전·검증 기록은 [`docs/testing/step4-ci-coderabbit.md`](../docs/testing/step4-ci-coderabbit.md)에 있다.

## 3. WAR

`./gradlew build`(또는 `:app:bootWar :app:war`)는 `app/build/libs/`에 두 파일을 만든다.

| 파일 | 용도 | 실행 |
| --- | --- | --- |
| `yhs-live-streaming-backend-0.0.1-SNAPSHOT.war` | 실행 가능한 WAR (내장 Tomcat 포함, `WEB-INF/lib-provided`) | `java -jar app/build/libs/yhs-live-streaming-backend-0.0.1-SNAPSHOT.war` (JDK 17 이상) |
| `yhs-live-streaming-backend-0.0.1-SNAPSHOT-plain.war` | 외부 Tomcat 배포용 (내장 Tomcat 제외) | **Tomcat 11.0 이상**(Servlet 6.1)의 `webapps/`에 복사. Tomcat 10.1 이하는 안 된다 |

외부 Tomcat 배포 시 `LiveStreamingApplication`이 상속한 `SpringBootServletInitializer`가 애플리케이션을 시작한다.

## 4. 설정과 환경변수

설정 파일: `app/src/main/resources/application.yml`

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BACKEND_PORT` | `8080` | HTTP 포트 (`server.port`) |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | 없음 | PostgreSQL 접속 정보(Spring Boot 표준 이름). 설정 파일에 적지 않는다 |
| `MEMBER_PASSWORD_PEPPER_KEY`, `MEMBER_PASSWORD_PEPPER_KEY_ID` | 없음 | 비밀번호 해시 pepper 키(SecureRandom 256bit를 Base64로)와 키 ID. 없거나 형식이 틀리면 기동 실패([MB-D007](../docs/member/decisions.md#1-확정-결정)) |
| `MEMBER_PASSWORD_HIBP_BASE_URL` | `https://api.pwnedpasswords.com` | 유출 비밀번호 확인(HIBP range API) 주소. 서버에서 이 주소로 나가는 HTTPS 통신이 필요하고, 닿지 않으면 회원가입이 503이다([MB-D010](../docs/member/decisions.md#1-확정-결정)) |
| `SPRINGDOC_ENABLED` | `false` | API 문서(`/v3/api-docs`)·Swagger UI 켜기. 개발·테스트에서만 `true`([G-D008](../docs/policy.md#3-공통-결정-이력)) |
| `AUTH_JWT_KEY`, `AUTH_JWT_KEY_ID` | 없음 | Access Token(JWT HS256) 서명 키(SecureRandom 256bit, Base64)와 키 ID(`kid`). 없거나 형식이 틀리면 기동 실패([MB-D007](../docs/member/decisions.md#1-확정-결정), [MB-D012](../docs/member/decisions.md#1-확정-결정)) |
| `MEMBER_OTP_HMAC_KEY`, `MEMBER_OTP_HMAC_KEY_ID` | 없음 | OTP HMAC 키(SecureRandom 256bit, Base64)와 키 ID. pepper·JWT 키와 다른 값. 없거나 형식이 틀리면 기동 실패([MB-D015](../docs/member/decisions.md#1-확정-결정)) |
| `MEMBER_OTP_MAIL_FROM` | 없음 | OTP 메일 보내는 주소. 없으면 기동 실패([MB-D016](../docs/member/decisions.md#1-확정-결정)) |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`, `SPRING_DATA_REDIS_SSL_ENABLED` | Boot 기본(localhost:6379), 비밀번호 없음 | Redis 접속(로그인 세션·OTP·요청 제한). 쓸 수 없으면 가입·로그인·OTP·세션 검증이 503([MB-D019](../docs/member/decisions.md#1-확정-결정)) |
| `MEMBER_REDIS_TIMEOUT`, `MEMBER_REDIS_CONNECT_TIMEOUT` | `2s`, `1s` | Redis 명령·연결 타임아웃 |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | 없음 | OTP 메일 SMTP. 개발은 Mailpit(`localhost:1025`, 계정 없음), 운영은 Gmail SMTP(`smtp.gmail.com:587`, 앱 비밀번호). 호스트가 없으면 기동 실패 |
| `MEMBER_SMTP_AUTH`, `MEMBER_SMTP_STARTTLS` | 없음(필수) | SMTP 인증·STARTTLS. 개발(Mailpit) `false`, 운영(Gmail) **반드시 `true`**. `STARTTLS=true`면 TLS를 강제한다(`starttls.required`) |
| `MEMBER_SMTP_CONNECT_TIMEOUT_MS`, `MEMBER_SMTP_READ_TIMEOUT_MS`, `MEMBER_SMTP_WRITE_TIMEOUT_MS` | `3000`, `5000`, `5000` | SMTP 타임아웃(ms). 자동 재시도 없음 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 없음 | Google OIDC 로그인 클라이언트(Google Cloud Console의 웹 애플리케이션). 비어 있으면 기동 실패. 테스트·CI는 실제 값을 쓰지 않는다([MB-D023](../docs/member/decisions.md#1-확정-결정)) |
| `MEMBER_OAUTH_PUBLIC_BASE_URL` | 없음 | 브라우저가 보는 서비스 출처(경로 없는 `https://호스트[:포트]`, `http`는 `localhost`·`127.0.0.1`·`[::1]`만: `__Host-` 쿠키가 Secure라서). Google 콘솔의 승인된 Redirect URI는 `{이 값}/api/v1/auth/oauth2/code/google`이다. 비었거나 형식이 틀리면 기동 실패([MB-D024](../docs/member/decisions.md#1-확정-결정)) |

요청·로그인·OTP 제한 수치는 `member.limits.*` 설정으로 바꿀 수 있고 기본값이 정책 값이다([MB-D017](../docs/member/decisions.md#1-확정-결정), [MB-D018](../docs/member/decisions.md#1-확정-결정)). 요청 제한의 클라이언트 IP는 `getRemoteAddr()`만 쓴다. 리버스 프록시 뒤에 두면 모든 요청이 프록시 IP로 묶이므로 외부 공개 전에 [MB-U06](../docs/member/decisions.md#2-미결)을 정해야 한다.

원칙:

- 환경마다 다른 값은 `${ENV_NAME:기본값}`으로 받는다.
- **비밀번호·토큰·키는 기본값 없이 환경변수로만 받고, 설정 파일과 Git에 적지 않는다.**
- Spring Boot는 저장소 루트의 `.env`를 자동으로 읽지 않는다. 실행하는 셸이나 IDE 실행 구성에서 환경변수를 넣는다.

```powershell
$env:BACKEND_PORT = "18080"; .\gradlew.bat :app:bootRun          # PowerShell
```

```bash
BACKEND_PORT=18080 ./gradlew :app:bootRun                          # bash
```

Actuator는 `health`만 웹에 노출하고 상세 정보(`show-details: never`)는 숨긴다. health는 DB·Redis를 확인하고, 메일(SMTP)은 확인하지 않는다(`management.health.mail.enabled=false`: 확인마다 Gmail에 접속하지 않으려고). `/actuator/env` 등 다른 엔드포인트는 익명 요청이면 401, 인증된 요청이면 404다(테스트로 확인, [G-D011](../docs/policy.md#3-공통-결정-이력)).

## 5. 모듈과 패키지 규칙

```text
backend/
├── settings.gradle.kts          # 모듈 목록, 저장소(mavenCentral) 선언
├── gradle/libs.versions.toml    # 고정 버전 (Spring Boot 등)
├── build-logic/                 # 공통 빌드 규칙: yhs.java-conventions (Java 17, UTF-8, JUnit, Boot BOM, Spring 버전 일치 검사)
├── app/                         # 실행 모듈: 진입점, 설정, global·도메인 모듈 조립, WAR
└── modules/                     # global + 도메인 모듈 (build-logic의 yhs.domain-module-conventions 적용)
    ├── global/       # 공통 기반: 공통 예외·오류 응답, 보안 기본 체인, 인증 계약, OpenAPI 공통 설정
    ├── member/       # 회원: 회원가입, 로그인, OTP, OAuth, 회원 정보
    ├── broadcast/    # 방송: 방송 생성·수정·조회, 방송 상태 관리
    ├── streaming/    # 스트리밍: MediaMTX 연동, WHIP 송출·WHEP 시청 인증, 방송 송출 제어
    ├── chat/         # 채팅: STOMP/WebSocket 실시간 채팅
    ├── comment/      # 댓글
    ├── donation/     # 후원: 후원 요청·결제(TossPayments)·내역
    └── recording/    # 녹화: 방송 녹화, 녹화 파일 저장(S3)·조회
```

### 도메인 모듈 (Q9: 도메인 기준 분할)

각 도메인 모듈에는 `modules/<domain>/build.gradle.kts`와 기본 패키지 `com.yhs.livestreaming.<domain>`(`package-info.java`)만 있다. 의존성과 계층 패키지는 구현할 때 추가한다. `app`은 global과 7개 도메인 모듈을 모두 `implementation(project(...))`로 조립하고, 모듈 jar는 WAR의 `WEB-INF/lib/yhs-live-streaming-<module>-<version>.jar`로 들어간다.

공통 기반 `modules/global/`(Issue #8, [G-D001](../docs/policy.md#3-공통-결정-이력))은 공통 예외·오류 응답, 보안 기본 체인, 인증 계약, OpenAPI 공통 설정을 둔다. global은 업무 규칙이 없어 계층 패키지 대신 기능별 패키지(`error`·`security`·`auth`·`openapi`·`persistence`·`time`·`scheduling`)를 쓴다. 다른 모듈이 쓰는 공개 API는 [docs/global/module.md](../docs/global/module.md) 3장이다.

모듈 안에서 계층을 패키지로 나눈다.

| 패키지 | 내용 |
| --- | --- |
| `in` | 들어오는 어댑터: REST Controller, JSP Controller, STOMP 핸들러, Request/Response DTO |
| `app` | 유스케이스 서비스, 포트 인터페이스(out 포트) |
| `domain` | 엔티티, 값 객체, 도메인 규칙. 프레임워크 의존 최소화 |
| `out` | 나가는 어댑터: JPA/MyBatis Repository, Redis, MediaMTX·결제·S3 클라이언트 |

- 의존 방향: `in → app → domain`, `out → app(포트 구현)·domain`. `app`은 `out`을 직접 참조하지 않고 포트 인터페이스로만 쓴다.
- 다른 도메인은 그 도메인의 `app` 공개 API로만 호출한다. 다른 도메인의 `domain`·`out`을 직접 참조하지 않는다. 공개 패키지와 자동 검사(ArchUnit)는 [G-D015](../docs/policy.md#3-공통-결정-이력)를 따른다(member는 `member.app.api`).
- 계층 패키지(`in/app/domain/out`)는 미리 만들지 않는다(Q2). 그 계층의 코드를 처음 넣을 때 만든다.

### 도메인 구현을 시작할 때

1. 필요한 의존성만 `modules/<domain>/build.gradle.kts`에 추가한다.
   ```kotlin
   plugins {
       id("yhs.domain-module-conventions")
   }
   dependencies {
       implementation("org.springframework.boot:spring-boot-starter-webmvc")  // 예: Controller가 생길 때
   }
   ```
2. 코드는 `modules/<domain>/src/main/java/com/yhs/livestreaming/<domain>/{in,app,domain,out}`에 둔다. `app` 모듈의 `@SpringBootApplication`이 `com.yhs.livestreaming` 아래를 스캔하므로 추가 설정이 필요 없다.
3. 테스트는 `modules/<domain>/src/test/java`에 둔다. Spring 컨텍스트가 필요한 통합 테스트는 `app` 모듈에 둔다(도메인 모듈에는 `@SpringBootApplication`이 없다).

### 새 모듈 추가 절차 (예: `global`)

1. `settings.gradle.kts`의 `include(...)`에 `":modules:global"` 추가
2. `modules/global/build.gradle.kts`에 `plugins { id("yhs.domain-module-conventions") }`
3. `app/build.gradle.kts`에 `implementation(project(":modules:global"))` 추가
4. 기본 패키지 `com.yhs.livestreaming.global`에 `package-info.java`
5. 도메인 모듈이면 app 테스트 `ModuleBoundaryTest`의 도메인 모듈 목록에 기본 패키지를 더한다([G-D015](../docs/policy.md#3-공통-결정-이력))

## 6. 의존성 도입 계획 (Q3)

지금은 `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, `spring-boot-starter-tomcat-runtime`(providedRuntime), `spring-boot-starter-test`와 global 모듈의 `spring-boot-starter-security`·`-validation`·`-data-jpa`·springdoc·`spring-security-oauth2-jose`, member 모듈의 `spring-boot-starter-data-redis`·`-mail`·`-security-oauth2-client`, app의 `spring-boot-starter-flyway`·PostgreSQL 드라이버·`flyway-database-postgresql`, 테스트용 `spring-boot-starter-webmvc-test`(app)·`spring-boot-starter-security-test`(app, global)·`spring-boot-starter-data-jpa-test`(member)·`spring-boot-testcontainers`와 Testcontainers PostgreSQL(app, member)·`spring-boot-starter-data-redis-test`(member)·Redis 테스트용 `spring-boot-starter-data-redis`(app)·가짜 OIDC 공급자 서명용 `spring-security-oauth2-jose`(app 테스트)·모듈 경계 검사용 ArchUnit(app 테스트)이 있다. 나머지는 아래 시점에 추가한다. 실행·테스트 클래스패스를 해석할 때 Boot·Framework 모듈 버전이 섞이면 실패한다([G-D012](../docs/policy.md#3-공통-결정-이력)). 이 검사는 IDE의 Gradle 동기화에도 똑같이 걸리므로, 동기화가 "Spring 버전 불일치"로 실패하면 메시지에 나온 의존성을 확인한다. 버전은 Spring Boot 4.1.3 BOM 기준이며, BOM이 관리하지 않는 것만 `gradle/libs.versions.toml`에 고정한다.

| 기술 | 도입 시점 | 버전 (4.1.3 기준) | 주의 |
| --- | --- | --- | --- |
| JPA (Hibernate), PostgreSQL | 첫 엔티티 (Member, BE-02, 도입함) | Hibernate 7.4.15, JDBC 42.7.14 (BOM) | 넣으면 DataSource 자동 설정 때문에 DB 없이 실행되지 않는다. 테스트는 Testcontainers([G-D009](../docs/policy.md#3-공통-결정-이력)), 공통 설정 위치는 [G-D010](../docs/policy.md#3-공통-결정-이력) |
| Flyway | 첫 테이블 (BE-02, 도입함) | 12.4.0 (BOM) | PostgreSQL은 `flyway-database-postgresql` 모듈도 필요. 마이그레이션은 각 모듈 `db/migration`, 버전은 날짜형 `V<yyyymmdd>_<순번>` |
| Spring Security | global 공통 기반 (Issue #8) | 7.1.3 (BOM) | 넣으면 모든 URL에 인증이 걸린다. 기본 체인은 [G-D004](../docs/policy.md#3-공통-결정-이력), 인증 방식은 [MB-D002](../docs/member/decisions.md#1-확정-결정) |
| Redis | 세션·OTP (BE-03, 도입함: member `spring-boot-starter-data-redis`) | Lettuce 7.5.2 (BOM) | 테스트는 Testcontainers `redis:7.4-alpine`. 원자 처리는 Lua 스크립트(`member/src/main/resources/redis/member`) |
| Spring Security JOSE(Nimbus) | Access Token JWT (BE-03, 도입함: global `spring-security-oauth2-jose`) | Security 7.1.3 / nimbus-jose-jwt 10.9.1 (BOM) | resource-server 스타터는 쓰지 않는다(쿠키 인증 필터를 직접 둔다, [MB-D012](../docs/member/decisions.md#1-확정-결정)) |
| Spring Mail | OTP 메일 (BE-03, 도입함: member `spring-boot-starter-mail`) | Angus Mail 2.0.5 (BOM) | 테스트는 대역 + Mailpit 컨테이너(`axllent/mailpit:v1.21.0`) |
| Spring Security OAuth2 Client | Google OIDC 로그인 (BE-05, 도입함: member `spring-boot-starter-security-oauth2-client`) | Security 7.1 (BOM) | Boot 4 스타터 이름이다. 코드는 `oauth2Login`·`AuthorizationRequestRepository` 등 Security 6.5 호환 API만 쓴다. 테스트는 JDK HttpServer 가짜 OIDC 공급자(app 테스트 의존 `spring-security-oauth2-jose`로 서명) |
| springdoc OpenAPI | global 공통 기반 (Issue #8) | 3.1.1 (Boot 4.1.0으로 빌드된 릴리스) — BOM 미관리, `libs.versions.toml` | Boot 3용 2.x, Boot 4.2용 3.2.x는 쓸 수 없다(4.2.x 모듈을 끌어와 버전이 섞인다). 운영 기본 비활성([G-D008](../docs/policy.md#3-공통-결정-이력), [G-D012](../docs/policy.md#3-공통-결정-이력)) |
| JSP / JSTL | 관리자 화면 | Jasper 11.0.27, JSTL API 3.0.2, 구현체 3.0.1 (BOM) | 7장 확인 결과 참고 |
| WebSocket / STOMP | Chat | `spring-boot-starter-websocket` (BOM) | |
| ArchUnit (`archunit-junit5`) | 모듈 경계 검사 (BE-07, 도입함: app 테스트) | 1.5.1 — BOM 미관리, `libs.versions.toml` | 테스트 의존성만. 규칙은 [G-D015](../docs/policy.md#3-공통-결정-이력) |
| MyBatis | 복잡한 조회가 실제로 필요할 때 | starter 4.x (확인 시점 4.1.0) — BOM 미관리 | Boot 3용 3.0.x는 쓸 수 없다 |

## 7. JSP 확인 결과 (Q6: 저장소 밖 임시 실험)

2026-11-24, 이 저장소의 `backend/`를 저장소 밖에 복사해 JSP 설정을 넣고 확인했다. 실험 코드는 저장소에 넣지 않았다.

실험 구성:

```kotlin
// app/build.gradle.kts
providedRuntime("org.apache.tomcat.embed:tomcat-embed-jasper")      // 외부 Tomcat은 자체 Jasper 사용
implementation("jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api")
implementation("org.glassfish.web:jakarta.servlet.jsp.jstl")
```

```yaml
spring.mvc.view.prefix: /WEB-INF/views/
spring.mvc.view.suffix: .jsp
```

JSP 위치는 `app/src/main/webapp/WEB-INF/views/*.jsp`, taglib URI는 `jakarta.tags.core`, `jakarta.tags.functions`.

| 실행 방식 | 결과 |
| --- | --- |
| 실행 가능한 WAR (`java -jar`, JDK 17.0.16) | 성공. Tomcat 11.0.27, JSP 4.0. `c:out`(HTML 이스케이프), `c:forEach`, `fn:length`, EL, 스크립틀릿 표현식 정상 |
| 외부 Tomcat 11.0.29 (Docker `tomcat:11.0-jdk17`, plain WAR) | 성공. JSP 4.0, 같은 페이지 정상 렌더링 |
| 실행 가능한 JAR | 해당 없음. Spring Boot 문서상 JSP는 WAR 패키징에서만 지원 |

관리자 화면 단계에서는 위 의존성과 설정을 그대로 추가하면 된다.

## 8. 버전, 지원 기간, 미결 사항

- **Spring Boot 4.1.x OSS 지원은 2027-07-31까지**(상용 연장 ~2028-07). 그전에 4.2 이후로 올리는 작업을 계획한다. (참고: 4.2.0은 2026-11-20 출시, OSS ~2027-12-31)
- Boot 3.x 기준 자료와 다른 점: 웹 스타터 이름 `spring-boot-starter-webmvc`, 외부 Tomcat용 `spring-boot-starter-tomcat-runtime`, 외부 Tomcat은 11.0 이상, springdoc 3.x, MyBatis starter 4.x, Flyway 12, Hibernate 7, JUnit Jupiter 6.
- Gradle 9.9.2: Spring Boot 4.1이 지원하는 Gradle은 8.14 이상과 9.x다. wrapper jar SHA-256은 Gradle 공식 값(`621008af…3cdaf8`)과 일치하고, 배포본은 `distributionSha256Sum`으로 검증한다.

미결 (각 단계에서 결정):

- 인증 방식: [MB-U01](../docs/member/decisions.md#2-미결) — 확정([MB-D002](../docs/member/decisions.md#1-확정-결정))
- DB 연동 테스트 방식: [G-U01](../docs/policy.md#4-공통-미결) — 확정([G-D009](../docs/policy.md#3-공통-결정-이력))
- Spring Boot 4.2+ 업그레이드 시점.

## 9. 결정 사항 (Step 3, 2026-11-24)

| ID | 결정 |
| --- | --- |
| Q1 | 멀티 모듈 Gradle 구성 |
| Q2 | 계층 패키지(`in/app/domain/out`)는 구현할 때 만든다. **(2026-11-24 변경)** 도메인 기본 패키지 `com.yhs.livestreaming.<domain>`은 모듈 생성과 함께 `package-info.java`로 만든다 |
| Q3 | 초기 의존성은 Web MVC, Actuator, Test, WAR/Tomcat만. 나머지는 6장 시점에 추가 |
| Q4 | 상태 확인은 Actuator `/actuator/health`만 노출. 확인용 Controller는 만들지 않는다 |
| Q5 | DB·Redis 없이 실행·테스트되게 유지. PostgreSQL·Redis 연결은 후속 Step **(2026-11-26 변경: [G-D009](../docs/policy.md#3-공통-결정-이력) — DB 연동 테스트는 Testcontainers, 단위 테스트는 외부 인프라 없이)** |
| Q6 | WAR·Tomcat만 넣고, JSP는 저장소 밖 임시 실험으로 확인(7장). Jasper·JSTL은 관리자 화면 단계에서 추가 |
| Q7 | Spring Boot 4.1.x (3.5.x는 2026-06-30 OSS 지원 종료로 제외) |
| Q8 | 패키지 `com.yhs.livestreaming`, 클래스 `LiveStreamingApplication`, 프로젝트 `yhs-live-streaming-backend` |
| Q9 | 도메인 기준 모듈(`modules/<domain>`) + 실행 모듈 `app`. 계층은 모듈 안의 패키지(`in/app/domain/out`)로 나눈다 |
| Q10 | ~~지금은 `app`과 `build-logic`만 만든다~~ **(2026-11-24 변경, 사용자 지시)** 7개 도메인 모듈(`member`, `broadcast`, `streaming`, `chat`, `comment`, `donation`, `recording`)을 Gradle 설정과 기본 패키지까지 만든다. `global`은 공통 코드가 생길 때 추가 |

## 10. 검증 기록

| 일시 (KST) | 검증 | 결과 |
| --- | --- | --- |
| 2026-11-24 | Windows, Gradle 9.9.2(실행 JDK 25) + toolchain JDK 17.0.16: `gradlew build` | 성공. 테스트 3개, 실패 0. 클래스 파일 major 61(Java 17) |
| 2026-11-24 | `gradlew clean build -q` 1회 | **실패 1회, 원인 미확인** (`-q`로 오류 메시지가 가려짐). 이후 `clean build` 3회 연속 성공, 재현 안 됨 |
| 2026-11-24 | 실행 가능한 WAR, JDK 17, `BACKEND_PORT=18080` | `/actuator/health` 200 UP, `/actuator/env` 404, 8080 미사용 |
| 2026-11-24 | `gradlew :app:bootRun` (기본 포트) | `/actuator/health` 200 UP, Java 17.0.16 |
| 2026-11-24 | Linux: Docker `eclipse-temurin:17-jdk`(17.0.21.1), Git 인덱스 사본으로 `./gradlew build` | 성공. 테스트 3개, 실패 0 |
| 2026-11-24 | 외부 Tomcat 11.0.29 (Docker `tomcat:11.0-jdk17`)에 plain WAR 배포 | `/actuator/health` 200 UP |
| 2026-11-24 | JSP 임시 실험 (7장) | 실행 가능한 WAR, 외부 Tomcat 11 모두 성공 |
| 2026-11-24 | 도메인 모듈 7개 추가 후 `gradlew clean build` | 성공. `app` 테스트 3개 중 실패 0, 도메인 모듈은 테스트 없음(NO-SOURCE). 실행 가능한 WAR와 plain WAR 모두 `WEB-INF/lib`에 모듈 jar 7개 포함 |
| 2026-11-24 | 도메인 모듈 추가 후 Linux: Docker `eclipse-temurin:17-jdk`(17.0.21.1), Git 인덱스 사본으로 `./gradlew clean build` | 성공. 모듈 `:app` + `:modules:*` 7개 인식, 테스트 3개 중 실패 0, 도메인 jar 7개, WAR 2개(실행 가능, plain) 생성, 실행 가능한 WAR에 모듈 jar 7개 포함 |
