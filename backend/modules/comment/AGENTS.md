# comment 모듈 AGENTS

comment 모듈 전용 규칙만 둔다. 공통 규칙은 [루트 AGENTS.md](../../../AGENTS.md)를 따르고 여기서 반복하지 않는다.

## 1. 역할과 책임

- 댓글: 방송·영상 댓글([build.gradle.kts](build.gradle.kts) 주석,
  [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)).

## 2. 코드 작업 범위

- 이 폴더(`backend/modules/comment/`)와 기본 패키지 `com.yhs.livestreaming.comment`.
- Spring 컨텍스트가 필요한 통합 테스트의 위치는 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙) "도메인 구현을 시작할 때"를 따른다.

## 3. 문서 진입점

- [docs/comment/INDEX.md](../../../docs/comment/INDEX.md)

## 4. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 결정·미결 | [docs/comment/decisions.md](../../../docs/comment/decisions.md) |
| 설계·As-Is | [docs/comment/module.md](../../../docs/comment/module.md) |
| 합격 기준 | [docs/comment/test-spec.md](../../../docs/comment/test-spec.md) |
| 실행 결과 | [docs/comment/regression-log.md](../../../docs/comment/regression-log.md) |
| 진행 상태 | [docs/comment/PROGRESS.md](../../../docs/comment/PROGRESS.md) |

## 5. 모듈 경계와 개발 규칙

- 모듈 경계·패키지 규칙은 [루트 AGENTS.md](../../../AGENTS.md)와 [backend/README.md 5장](../../README.md#5-모듈과-패키지-규칙)이
  원본이다. comment 전용으로 확정된 규칙은 아직 없다.

## 6. 미정 규칙 상태

- 미정 (결정 전)
