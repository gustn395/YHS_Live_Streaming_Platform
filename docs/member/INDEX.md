# member — 문서 INDEX

member 모듈 문서의 시작점이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 문서 목록

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](../../backend/modules/member/AGENTS.md) | 모듈 전용 규칙과 코드 작업 범위 |
| [CONTEXT.md](CONTEXT.md) | 모듈 역할·액터·유스케이스·모듈 용어 |
| [decisions.md](decisions.md) | 결정 이력(`MB-D###`)·미결(`MB-U##`) |
| [module.md](module.md) | 설계(To-Be)·현재 상태(As-Is)·공개 API·의존·데이터 모델 |
| [test-spec.md](test-spec.md) | 요구사항·합격 기준(`MB-T###`) |
| [regression-log.md](regression-log.md) | 실제 실행한 테스트 결과 |
| [PROGRESS.md](PROGRESS.md) | 작업 상태(`MB-W##`)·차단 요소 |

## 2. 읽는 순서

1. [AGENTS.md](../../backend/modules/member/AGENTS.md) → 이 문서 3장에서 작업 행을 고른다.
2. 그 행의 문서에서 필요한 절만 ID로 찾아 읽는다.
3. 공통 규칙·미결은 [policy.md](../policy.md), 공통 용어는 [GLOSSARY.md](../GLOSSARY.md)에서 확인한다.

## 3. 작업 유형별 문서

| 작업 | 먼저 읽을 문서 |
| --- | --- |
| 정책 결정·미결 확인 | [decisions.md](decisions.md) 2장, [policy.md](../policy.md) 1·2·4장 |
| 용어·도메인 모델 | [CONTEXT.md](CONTEXT.md), [GLOSSARY.md](../GLOSSARY.md) |
| 구현 계획·구현 | [module.md](module.md), [test-spec.md](test-spec.md), [AGENTS.md](../../backend/modules/member/AGENTS.md), [backend/README.md](../../backend/README.md) 5·6장 |
| 테스트 결과 기록 | [regression-log.md](regression-log.md), [test-spec.md](test-spec.md) |
| 진행 상태 확인 | [PROGRESS.md](PROGRESS.md) |

## 4. 원본 관계

원본 관계는 [docs/INDEX.md 2장](../INDEX.md#2-원본-관계)을 따른다. 이 모듈에 따로 정한 예외는 없다.

## 5. 수정 기준

- 결정이 생기면 [decisions.md](decisions.md)에 새 `MB-D###`을 쓰고, 현재 규칙이 바뀌면 [policy.md](../policy.md) 2장을 고친다.
- 코드를 바꾸면 [module.md](module.md) As-Is를 코드 기준으로 고친다.
- 테스트를 실행하면 [regression-log.md](regression-log.md)에, 상태가 바뀌면 [PROGRESS.md](PROGRESS.md)에 적는다.
- 역할·구조가 바뀔 때만 이 문서를 고친다.
