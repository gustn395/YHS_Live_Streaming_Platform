# global — 문서 INDEX

global(공통 기반) 모듈 문서의 시작점이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 문서 목록

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](../../backend/modules/global/AGENTS.md) | 모듈 전용 규칙과 코드 작업 범위 |
| [CONTEXT.md](CONTEXT.md) | 모듈 역할·사용하는 모듈·모듈 용어 |
| [decisions.md](decisions.md) | global 결정은 [policy.md](../policy.md) `G-D`·`G-U`에 둔다(G-D002) |
| [module.md](module.md) | 설계(To-Be)·현재 상태(As-Is)·공개 API·의존 |
| [test-spec.md](test-spec.md) | 요구사항·합격 기준(`GL-T###`) |
| [regression-log.md](regression-log.md) | 실제 실행한 테스트 결과 |
| [PROGRESS.md](PROGRESS.md) | 작업 상태(`GL-W##`)·차단 요소 |

## 2. 읽는 순서

1. [AGENTS.md](../../backend/modules/global/AGENTS.md) → 이 문서 3장에서 작업 행을 고른다.
2. 결정은 [policy.md](../policy.md) 1·3·4장에서 `G-D`·`G-U` ID로 찾는다.

## 3. 작업 유형별 문서

| 작업 | 먼저 읽을 문서 |
| --- | --- |
| 정책 결정·미결 확인 | [policy.md](../policy.md) 1·3·4장 |
| 공통 기반을 쓰는 모듈 구현 | [module.md](module.md) 3장 공개 API |
| global 구현 계획·구현 | [module.md](module.md), [test-spec.md](test-spec.md), [AGENTS.md](../../backend/modules/global/AGENTS.md), [backend/README.md](../../backend/README.md) 5·6장 |
| 테스트 결과 기록 | [regression-log.md](regression-log.md), [test-spec.md](test-spec.md) |
| 진행 상태 확인 | [PROGRESS.md](PROGRESS.md) |

## 4. 원본 관계

[docs/INDEX.md 2장](../INDEX.md#2-원본-관계)을 따른다. 예외: global의 결정·미결은 `decisions.md`가 아니라 `docs/policy.md` 3·4장이 원본이다(G-D002).

## 5. 수정 기준

- 결정이 생기면 [policy.md](../policy.md) 3장에 새 `G-D###`을 쓰고, 현재 규칙이 바뀌면 1장을 고친다.
- 코드를 바꾸면 [module.md](module.md) As-Is를 코드 기준으로 고친다.
- 테스트를 실행하면 [regression-log.md](regression-log.md)에, 상태가 바뀌면 [PROGRESS.md](PROGRESS.md)에 적는다.
