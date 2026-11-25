# broadcast 모듈 AGENTS

broadcast 모듈 전용 규칙만 둔다. 공통 규칙은 [루트 AGENTS.md](../../../AGENTS.md)를 따르고 여기서 반복하지 않는다.

## 1. 역할과 책임

- 방송: 방송 생성·수정·조회, 방송 상태 관리([build.gradle.kts](build.gradle.kts) 주석,
  [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)).

## 2. 코드 작업 범위

- 이 폴더(`backend/modules/broadcast/`)와 기본 패키지 `com.yhs.livestreaming.broadcast`.
- Spring 컨텍스트가 필요한 통합 테스트의 위치는 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙) "도메인 구현을 시작할 때"를 따른다.

## 3. 문서 진입점

- [docs/broadcast/INDEX.md](../../../docs/broadcast/INDEX.md)

## 4. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 결정·미결 | [docs/broadcast/decisions.md](../../../docs/broadcast/decisions.md) |
| 설계·As-Is | [docs/broadcast/module.md](../../../docs/broadcast/module.md) |
| 합격 기준 | [docs/broadcast/test-spec.md](../../../docs/broadcast/test-spec.md) |
| 실행 결과 | [docs/broadcast/regression-log.md](../../../docs/broadcast/regression-log.md) |
| 진행 상태 | [docs/broadcast/PROGRESS.md](../../../docs/broadcast/PROGRESS.md) |

## 5. 모듈 경계와 개발 규칙

- 모듈 경계·패키지 규칙은 [루트 AGENTS.md](../../../AGENTS.md)와 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)이
  원본이다. broadcast 전용으로 확정된 규칙은 아직 없다.

## 6. 미정 규칙 상태

- 미정 (결정 전)
