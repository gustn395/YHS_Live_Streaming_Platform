# global 모듈 AGENTS

global 모듈 전용 규칙만 둔다. 공통 규칙은 [루트 AGENTS.md](../../../AGENTS.md)를 따르고 여기서 반복하지 않는다.

## 1. 역할과 책임

- 공통 기반: 공통 예외·오류 응답, 보안 기본 구성, 공통 인증 계약, OpenAPI 공통 설정([G-D001](../../../docs/policy.md#3-공통-결정-이력)).
- 업무 규칙(회원·방송 등)은 두지 않는다. 도메인 모듈의 경로·타입을 직접 참조하지 않고, 확장점(`PublicEndpointContributor`, `LoginSessionVerifier`)으로 받는다.

## 2. 코드 작업 범위

- 이 폴더(`backend/modules/global/`)와 기본 패키지 `com.yhs.livestreaming.global`.
- 패키지는 기능별(`error`·`security`·`auth`·`openapi`)이다. 구조는 [docs/global/module.md](../../../docs/global/module.md) 1장.
- Spring 컨텍스트가 필요한 통합 테스트는 `app` 모듈에 둔다([backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)).

## 3. 문서 진입점

- [docs/global/INDEX.md](../../../docs/global/INDEX.md)

## 4. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 결정·미결 | [docs/policy.md](../../../docs/policy.md) 3·4장 (`G-D`·`G-U`) |
| 설계·As-Is | [docs/global/module.md](../../../docs/global/module.md) |
| 합격 기준 | [docs/global/test-spec.md](../../../docs/global/test-spec.md) |
| 실행 결과 | [docs/global/regression-log.md](../../../docs/global/regression-log.md) |
| 진행 상태 | [docs/global/PROGRESS.md](../../../docs/global/PROGRESS.md) |

## 5. 모듈 경계와 개발 규칙

- 보안 체인·오류 응답·인증 계약을 바꾸는 작업은 보안 변경이다(보안 리뷰 대상).
- `BusinessException`의 detail은 응답 본문에 그대로 나간다. 사용자에게 보여 줄 문장만 넣고, 비밀번호·토큰·키·내부 식별자·예외 원문은 넣지 않는다.
- 새 의존성이 Spring Boot·Framework 버전을 올리면 빌드가 실패한다([G-D012](../../../docs/policy.md#3-공통-결정-이력)). 같은 Boot 마이너 버전으로 빌드된 릴리스를 고른다.

## 6. 미정 규칙 상태

- [G-U02](../../../docs/policy.md#4-공통-미결) CSRF 방식 미결 — 쿠키 인증을 넣기 전에 정한다. 그 전까지 쿠키 인증을 넣지 않는다([G-D005](../../../docs/policy.md#3-공통-결정-이력)).
