# 업무 규칙 (policy)

현재 유효한 업무 규칙의 원본이다. 결정의 이유·이력은 모듈 `decisions.md`(모듈 결정)와 이 문서 3장(공통 결정)에 둔다.
작성 규칙: [docs/INDEX.md 4장](INDEX.md#4-작성-규칙)

## 1. 공통 규칙

- 오류 응답은 ProblemDetail(RFC 9457) + `code` + `errors[]` 형식이다. Spring Security의 401·403도 같다. 성공 응답은 감싸지 않는다([G-D003](#3-공통-결정-이력)).
- 보안 체인은 stateless다. 각 모듈이 등록한 공개 경로, `/actuator/health`, 개발·테스트의 Swagger만 인증 없이 허용하고 나머지는 인증이 필요하다([G-D004](#3-공통-결정-이력), [G-D011](#3-공통-결정-이력)).
- CSRF 보호가 꺼진 동안에는 쿠키 기반 인증을 도입하지 않고, 인증 기능을 외부에 공개하지 않는다([G-D005](#3-공통-결정-이력), [G-U02](#4-공통-미결)).
- Boot 기본 계정·HTTP Basic·폼 로그인을 쓰지 않는다([G-D013](#3-공통-결정-이력)).
- 다른 모듈은 현재 회원을 `@CurrentMember`(`AuthenticatedMember`)로 받는다([G-D006](#3-공통-결정-이력)).
- springdoc 문서·Swagger UI는 운영 기본값이 비활성이다([G-D008](#3-공통-결정-이력)). springdoc은 Boot와 같은 마이너 버전으로 빌드된 릴리스를 쓰고, Boot·Framework 모듈 버전이 섞이면 빌드가 실패한다([G-D012](#3-공통-결정-이력)).
- 단위 테스트는 외부 인프라 없이 실행하고, DB 연동 테스트는 Testcontainers를 쓴다([G-D009](#3-공통-결정-이력)).

## 2. 모듈별 규칙

### member

- 인증은 JWT Access Token + 불투명 Refresh Token이다([MB-D002](member/decisions.md#1-확정-결정)).
- 일반 가입 직후 계정은 PENDING·이메일 미확인이고, 첫 로그인 OTP로 ACTIVE가 된다. PENDING은 24시간 뒤 정리 대상이다([MB-D003](member/decisions.md#1-확정-결정)).
- 비밀번호는 NFC 기준 15~64자, 구성 규칙 없음, username·서비스 이름 파생어 거부([MB-D004](member/decisions.md#1-확정-결정), [MB-D005](member/decisions.md#1-확정-결정)).
- 유출 비밀번호는 HIBP range API(k-익명성)로 검사해 거부하고, 검사할 수 없으면 가입을 거부한다(503)([MB-D010](member/decisions.md#1-확정-결정)).
- 비밀번호는 PBKDF2-HMAC-SHA256 + pepper로 해시하고 원문을 저장·로깅하지 않는다([MB-D006](member/decisions.md#1-확정-결정)).
- 비밀 키는 용도별로 분리하고 기본값 없는 환경변수로만 받으며, 잘못되면 기동에 실패한다([MB-D007](member/decisions.md#1-확정-결정)).
- username·이메일은 소문자로 정규화하고 중복이면 409다. 회원가입에서만 중복을 안내한다([MB-D008](member/decisions.md#1-확정-결정), [MB-D009](member/decisions.md#1-확정-결정)).

## 3. 공통 결정 이력

| ID | 결정 | 이유 | 출처·날짜 |
| --- | --- | --- | --- |
| G-D001 | 공통 기반은 별도 `global` 모듈로 Member보다 먼저 만든다. 범위: 공통 예외, 공통 오류 응답, 공통 인증 계약, 공통 보안 구성 요소, OpenAPI 공통 설정. | Member 명세의 선행 작업(Global Security)이 비어 있고, Security 필터 체인은 앱 전체에 하나라 member 안에 두면 다른 모듈의 규칙까지 member가 알아야 한다. | Member 1차 Q3·6차 Q2, Issue #8, 2026-11-26 |
| G-D002 | global 문서는 `docs/global/`에 모듈 문서 7종으로 두고, 테스트·작업 ID는 `GL-T###`·`GL-W##`을 쓴다. global의 결정·미결은 이 문서의 `G-D`·`G-U`에 둔다. | 다른 모듈과 같은 Workflow로 As-Is·테스트·진행을 관리한다. | Member 3차 Q1, 2026-11-26 |
| G-D003 | 오류 응답은 ProblemDetail(RFC 9457) + 확장 필드 `code`(기계용 오류 코드) + 검증 오류 `errors[]`(필드·메시지)다. Spring Security의 401·403에도 같은 형식을 쓴다. 성공 응답은 감싸지 않는다. | Spring 기본 지원이고 Boot 3.5와 호환되며, 같은 상태 코드 안에서 프론트가 원인을 구분할 수 있다. | Member 2차 Q2, R-22, 2026-11-26 |
| G-D004 | global에 stateless 기본 `SecurityFilterChain`을 둔다. 모듈이 등록한 공개 경로·`/actuator/health`·개발·테스트의 Swagger만 허용하고 나머지는 인증이 필요하다. JWT 검증 필터는 global에 두고 MB-W05에서 구현한다. | 회원가입 API가 처음부터 실제 보안 체인 아래에서 테스트되고 보안 리뷰를 받는다. | Member 2차 Q1·6차 Q2, 2026-11-26 |
| G-D005 | CSRF 보호는 global 단계에서 끈다. 꺼진 동안 쿠키 기반 인증을 도입하지 않고, CSRF가 꺼진 상태로 인증 기능을 외부에 공개하지 않는다. 활성화와 공격 방어 테스트는 MB-W05 완료 기준이다. | 인증 쿠키가 없는 단계에서는 막을 공격이 없고, 공개 API가 403이 되는 것을 피한다. 꺼진 사실은 이 결정과 테스트로 드러낸다. | Member 4차 Q1, R-16, 2026-11-26 |
| G-D006 | 공통 인증 계약: 인증 주체 `AuthenticatedMember(memberId, role, sessionId)`, 컨트롤러 인자 `@CurrentMember`, 로그인 세션 검증 포트 `LoginSessionVerifier`(인터페이스는 global, 구현은 member). | 다른 모듈이 member 내부나 JWT 클레임 구조에 의존하지 않는다. | Member 2차 Q3, 2026-11-26 |
| G-D007 | 로그인 세션 검증은 허용 목록 `auth:session:{sessionId}`로 한다. 세션이 없으면 401, 저장소 오류면 503이며 통과시키지 않는다. 세션 TTL은 절대 만료 시각 기준이다. JWT의 memberId와 세션 소유 회원이 같은지 검증한다. | 저장소 데이터가 사라져도 폐기한 세션이 통과하지 않고, 장애와 인증 실패를 구분한다. 명세 3.3의 `revoked` 키 예시를 대체한다. | Member 2차 Q4, R-10, R-11, 2026-11-26 |
| G-D008 | **(버전 부분 변경 → G-D012, 2026-11-26)** API 문서는 springdoc 3.2.1(Spring Boot 4.1.x 호환, `backend/gradle/libs.versions.toml`)을 쓴다. 공통 `OpenAPI` 설정은 global에 두고, 각 API의 Spec 인터페이스는 그 API를 구현할 때 작성한다. 개발·테스트에서는 허용하고 운영에서는 기본 비활성이다. | springdoc 공식 호환표(Boot 4.1.x ↔ 3.1.x~3.2.x). 오류 스키마를 한 곳에서 정의한다. | Member 4차 Q2, 2026-11-26 |
| G-D009 | DB 연동 테스트는 Testcontainers + `@ServiceConnection`으로 한다. 단위 테스트는 외부 인프라(DB·Redis·Docker) 없이 실행한다. [README 9장](../backend/README.md#9-결정-사항-step-3-2026-11-24) Q5의 "DB 없이 테스트"는 "테스트가 외부에 미리 준비된 DB에 의존하지 않는다"는 뜻으로 바꿔 읽는다. | 운영과 같은 PostgreSQL·Redis에서 부분 인덱스·원자 연산·장애를 검증한다. | Member 1차 Q4, R-23, 2026-11-26 (G-U01 확정) |
| G-D010 | DB 공통 설정은 global에, 스키마는 각 도메인 모듈에 둔다. JPA·PostgreSQL·Flyway·Testcontainers는 첫 엔티티 작업(BE-02, MB-W01)에서 도입한다. | [README 6장](../backend/README.md#6-의존성-도입-계획-q3)의 "첫 엔티티 때" 도입 시점과 맞춘다. | Member 5차 Q2·6차 Q1, 2026-11-26 |
| G-D011 | 노출하지 않은 Actuator 엔드포인트에 대한 익명 요청은 다른 보호 경로처럼 401이다. 비노출 여부는 인증된 요청이 404인지로 검증한다. | 공개 경로만 허용하는 G-D004와 같고, exposure 설정을 실수로 넓혀도 인증 없이 열리지 않는다. | Member 7차 Q1(Issue #8), 2026-11-26 |
| G-D012 | springdoc 버전은 3.1.1이다(G-D008의 3.2.1을 바꾼다. 나머지 G-D008 내용은 유지). springdoc은 같은 Spring Boot 마이너 버전으로 빌드된 릴리스를 쓰고, 빌드는 `org.springframework.boot` 모듈이 모두 Boot 버전과 같고 Spring Framework 모듈이 한 버전인지 검사한다. | springdoc 3.2.1은 Boot 4.2.0으로 빌드돼 `spring-boot-webmvc 4.2.0`·`spring-web 7.1.0`을 끌어오고, Gradle 충돌 해결로 Boot 4.1.3과 섞여 자동 설정이 깨졌다(관측 convention 빈 2개). 3.1.1은 Boot 4.1.0으로 빌드됐다(Maven Central POM 부모 버전). | Member 8차 Q1(Issue #8), 2026-11-26 |
| G-D013 | Spring Boot 기본 계정(인메모리 사용자와 기동 시 생성 비밀번호)을 쓰지 않는다(`UserDetailsServiceAutoConfiguration` 제외). 인증은 global 보안 체인과 member 모듈이 맡는다. HTTP Basic·폼 로그인 같은 기본 인증 방식은 켜지 않는다. | 기본 계정은 쓰는 곳이 없는데 생성 비밀번호가 로그에 남고, 인증 경로가 하나 더 생긴다. | Member 9차 Q2(Issue #8 S6 리뷰), 2026-11-26 |

## 4. 공통 미결

| ID | 미결 | 상태 | 출처·결정 시점 |
| --- | --- | --- | --- |
| G-U01 | DB 연동 테스트 방식(Testcontainers, Docker Compose 등): JPA 도입 단계에서 결정. | 확정 (G-D009) | `backend/README.md` 8장에서 이관(2026-11-25). 결정 시점 근거: [README 6장](../backend/README.md#6-의존성-도입-계획-q3) |
| G-U02 | CSRF 보호 방식(예: 쿠키 이중 제출 토큰)과 쿠키 속성. CSRF가 꺼진 동안의 조건은 G-D005. | 미결 | Member 4차 Q1(2026-11-26). 결정 시점: BE-03(MB-W05) 착수 전 |
