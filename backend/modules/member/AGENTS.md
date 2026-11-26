# member 모듈 AGENTS

member 모듈 전용 규칙만 둔다. 공통 규칙은 [루트 AGENTS.md](../../../AGENTS.md)를 따르고 여기서 반복하지 않는다.

## 1. 역할과 책임

- 회원: 회원가입, 로그인, OTP, OAuth, 회원 정보([build.gradle.kts](build.gradle.kts) 주석,
  [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)).

## 2. 코드 작업 범위

- 이 폴더(`backend/modules/member/`)와 기본 패키지 `com.yhs.livestreaming.member`.
- Spring 컨텍스트가 필요한 통합 테스트의 위치는 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙) "도메인 구현을 시작할 때"를 따른다.

## 3. 문서 진입점

- [docs/member/INDEX.md](../../../docs/member/INDEX.md)

## 4. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 결정·미결 | [docs/member/decisions.md](../../../docs/member/decisions.md) |
| 설계·As-Is | [docs/member/module.md](../../../docs/member/module.md) |
| 합격 기준 | [docs/member/test-spec.md](../../../docs/member/test-spec.md) |
| 실행 결과 | [docs/member/regression-log.md](../../../docs/member/regression-log.md) |
| 진행 상태 | [docs/member/PROGRESS.md](../../../docs/member/PROGRESS.md) |

## 5. 모듈 경계와 개발 규칙

- 모듈 경계·패키지 규칙은 [루트 AGENTS.md](../../../AGENTS.md)와 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)이
  원본이다. member 전용으로 확정된 규칙은 아직 없다.

## 6. 미정 규칙 상태

- [MB-U01](../../../docs/member/decisions.md#2-미결) 인증 방식 — 확정([MB-D002](../../../docs/member/decisions.md#1-확정-결정)). 쿠키·JWT 세부는 BE-03에서 정한다.
- 남은 member 미결: [MB-U02, MB-U04, MB-U05](../../../docs/member/decisions.md#2-미결). MB-U03(HIBP)은 [MB-D010](../../../docs/member/decisions.md#1-확정-결정)으로 확정.
- [G-U01](../../../docs/policy.md#4-공통-미결) DB 연동 테스트 방식 — 확정([G-D009](../../../docs/policy.md#3-공통-결정-이력)). 영속성 테스트 규칙은 BE-02(MB-W01)에서 정한다.
