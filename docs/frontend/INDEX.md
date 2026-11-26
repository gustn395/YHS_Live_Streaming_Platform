# frontend — 문서 INDEX

frontend(React SPA) 문서의 시작점이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 문서 목록

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](../../frontend/AGENTS.md) | frontend 전용 규칙과 코드 작업 범위 |
| [CONTEXT.md](CONTEXT.md) | 역할·액터·유스케이스·frontend 용어 |
| [decisions.md](decisions.md) | 결정 이력(`FE-D###`)·미결(`FE-U##`) |
| [module.md](module.md) | 설계(To-Be)·현재 상태(As-Is)·공개 API·의존·데이터 모델 |
| [test-spec.md](test-spec.md) | 요구사항·합격 기준(`FE-T###`) |
| [regression-log.md](regression-log.md) | 실제 실행한 테스트 결과 |
| [PROGRESS.md](PROGRESS.md) | 작업 상태(`FE-W##`)·차단 요소 |

## 2. 읽는 순서

1. [AGENTS.md](../../frontend/AGENTS.md) → 이 문서 3장에서 작업 행을 고른다.
2. 그 행의 문서에서 필요한 절만 ID로 찾아 읽는다.
3. 공통 규칙·미결은 [policy.md](../policy.md), 공통 용어는 [GLOSSARY.md](../GLOSSARY.md)에서 확인한다. Backend 오류 응답 형식은
   [docs/global/module.md](../global/module.md)에서 확인한다.

## 3. 작업 유형별 문서

| 작업 | 먼저 읽을 문서 |
| --- | --- |
| 정책 결정·미결 확인 | [decisions.md](decisions.md) 2장, [policy.md](../policy.md) 1·4장 |
| 용어·도메인 모델 | [CONTEXT.md](CONTEXT.md), [GLOSSARY.md](../GLOSSARY.md) |
| 구현 계획·구현 | [module.md](module.md), [test-spec.md](test-spec.md), [AGENTS.md](../../frontend/AGENTS.md), [decisions.md](decisions.md) 1장 |
| 테스트 결과 기록 | [regression-log.md](regression-log.md), [test-spec.md](test-spec.md) |
| 진행 상태 확인 | [PROGRESS.md](PROGRESS.md) |

## 4. 원본 관계

원본 관계는 [docs/INDEX.md 2장](../INDEX.md#2-원본-관계)을 따른다. 이 모듈의 예외:

- frontend는 Backend 모듈이 아니므로 모듈 전용 규칙의 원본은 `backend/modules/<module>/AGENTS.md`가 아니라
  [frontend/AGENTS.md](../../frontend/AGENTS.md)다.

## 5. 수정 기준

- 결정이 생기면 [decisions.md](decisions.md)에 새 `FE-D###`을 쓰고, 현재 규칙이 바뀌면 [policy.md](../policy.md) 2장을 고친다.
- 코드를 바꾸면 [module.md](module.md) As-Is를 코드 기준으로 고친다.
- 테스트를 실행하면 [regression-log.md](regression-log.md)에, 상태가 바뀌면 [PROGRESS.md](PROGRESS.md)에 적는다.
- 역할·구조가 바뀔 때만 이 문서를 고친다.
