# global — CONTEXT

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 모듈 역할

- 모든 도메인 모듈이 함께 쓰는 공통 기반: 공통 예외·오류 응답, 보안 기본 구성, 공통 인증 계약, OpenAPI 공통 설정([G-D001](../policy.md#3-공통-결정-이력)).
- 도메인 규칙을 두지 않는다. 회원·방송 같은 업무 규칙은 각 도메인 모듈에 둔다.

## 2. 액터

- 직접 액터는 없다. 사용하는 쪽은 도메인 모듈(member 등)과 `app` 실행 모듈이다.

## 3. 유스케이스

- 도메인 모듈이 업무 오류를 `ErrorCode` + `BusinessException`으로 던지면 ProblemDetail로 응답된다.
- 도메인 모듈이 공개 경로를 `PublicEndpointContributor`로 등록한다(예: 회원가입).
- 컨트롤러가 `@CurrentMember`로 현재 인증 주체를 받는다.
- member 모듈이 `LoginSessionVerifier`를 구현해 로그인 세션 검증을 제공한다(MB-W05~W08).

## 4. 모듈 용어

- 용어 확정은 member 정책 정리의 미결("인증 주체" 등)로 남아 있다. 확정되면 두 모듈 이상이 쓰는 용어는 [GLOSSARY.md](../GLOSSARY.md)에 둔다.
