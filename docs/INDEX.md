# 문서 INDEX

이 저장소 문서의 시작점이다. 여기서 대상 모듈을 정하고, 모듈 `INDEX.md`의 "작업 유형별 문서" 표를 따라 필요한 절만 읽는다.

## 1. 문서 지도

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | 프로젝트 공통 규칙 |
| [backend/README.md](../backend/README.md) | 기술 스택, 빌드·실행, 모듈 구조와 패키지 규칙, 의존성 도입 계획, 기존 결정 Q1~Q10 |
| [policy.md](policy.md) | 현재 유효한 업무 규칙, 공통 결정 이력(`G-D`), 공통 미결(`G-U`) |
| [GLOSSARY.md](GLOSSARY.md) | 두 모듈 이상이 쓰는 용어 |
| [testing/step4-ci-coderabbit.md](testing/step4-ci-coderabbit.md) | Step 4 CI·CodeRabbit·Ruleset 결정 Q1~Q7과 검증 |
| [testing/step4-5-agent-harness.md](testing/step4-5-agent-harness.md) | Step 4.5 Agent Harness·문서 구조 결정과 검증 |
| [testing/fe02-ci-split.md](testing/fe02-ci-split.md) | FE-02 Frontend CI·Backend/Frontend CI 독립성·Ruleset 전환 결정 Q1~Q12와 검증 |
| [testing/poc-mediamtx-webrtc.md](testing/poc-mediamtx-webrtc.md) | MediaMTX WebRTC 송출·시청 POC 기록 |

| 모듈 | 접두어 | 문서 | 모듈 규칙 |
| --- | --- | --- | --- |
| global (공통 기반) | GL | [global/INDEX.md](global/INDEX.md) | [AGENTS.md](../backend/modules/global/AGENTS.md) |
| member | MB | [member/INDEX.md](member/INDEX.md) | [AGENTS.md](../backend/modules/member/AGENTS.md) |
| broadcast | BC | [broadcast/INDEX.md](broadcast/INDEX.md) | [AGENTS.md](../backend/modules/broadcast/AGENTS.md) |
| streaming | ST | [streaming/INDEX.md](streaming/INDEX.md) | [AGENTS.md](../backend/modules/streaming/AGENTS.md) |
| chat | CH | [chat/INDEX.md](chat/INDEX.md) | [AGENTS.md](../backend/modules/chat/AGENTS.md) |
| comment | CM | [comment/INDEX.md](comment/INDEX.md) | [AGENTS.md](../backend/modules/comment/AGENTS.md) |
| donation | DN | [donation/INDEX.md](donation/INDEX.md) | [AGENTS.md](../backend/modules/donation/AGENTS.md) |
| recording | RC | [recording/INDEX.md](recording/INDEX.md) | [AGENTS.md](../backend/modules/recording/AGENTS.md) |
| frontend (React SPA) | FE | [frontend/INDEX.md](frontend/INDEX.md) | [AGENTS.md](../frontend/AGENTS.md) |

`docs/api/`, `docs/architecture/`는 아직 비어 있다(`.gitkeep`).

## 2. 원본 관계

같은 정보의 원문은 아래 원본 한 곳에만 둔다. 다른 문서에는 짧은 요지와 ID·링크만 둔다.

| 정보 | 원본 | 다른 문서에서는 |
| --- | --- | --- |
| 프로젝트 공통 규칙 | `AGENTS.md` | 반복하지 않고 링크한다 |
| 모듈 전용 규칙 | `backend/modules/<module>/AGENTS.md` | 루트 규칙을 반복하지 않는다 |
| 기술 스택·빌드·모듈 구조·의존성 도입 시점 | `backend/README.md` | 링크한다 |
| 현재 유효한 업무 규칙 | `docs/policy.md` | 규칙 문장을 복사하지 않고 링크한다 |
| 모듈 결정 이력·이유·미결 | `docs/<module>/decisions.md` (`<P>-D###`, `<P>-U##`) | ID로 참조한다 |
| 모듈 공통 결정 이력·미결 | `docs/policy.md` 3·4장 (`G-D###`, `G-U##`) | ID로 참조한다 |
| 두 모듈 이상이 쓰는 용어 | `docs/GLOSSARY.md` | 링크한다 |
| 모듈 배경·액터·유스케이스·모듈 전용 용어 | `docs/<module>/CONTEXT.md` | 공통 용어는 GLOSSARY 링크로 대신한다 |
| 확정 설계(To-Be)·코드 기준 현재 상태(As-Is)·공개 API | `docs/<module>/module.md` | 근거는 결정 ID로 적는다 |
| 요구사항·합격 기준·테스트 수 | `docs/<module>/test-spec.md` (`<P>-T###`) | 수치를 복사하지 않는다 |
| 실제 실행한 테스트 결과 | `docs/<module>/regression-log.md` | 실행하지 않은 것을 적지 않는다 |
| 작업 상태·차단 요소 | `docs/<module>/PROGRESS.md` (`<P>-W##`) | 이유·이력 문장을 쓰지 않는다 |
| Step 3 결정 Q1~Q10 | `backend/README.md` 9장 | 옮기지 않고 링크한다 |
| Step 4 결정 Q1~Q7 | `docs/testing/step4-ci-coderabbit.md` 1장 | 옮기지 않고 링크한다 |
| Step 4.5 결정 | `docs/testing/step4-5-agent-harness.md` 1장 | 옮기지 않고 링크한다 |
| FE-02 공통 CI 결정·검증 | `docs/testing/fe02-ci-split.md` 1장 | 옮기지 않고 링크한다. Frontend 전용 결정은 `docs/frontend/decisions.md` |

## 3. ID 체계

| 종류 | 형식 | 원본 위치 |
| --- | --- | --- |
| 결정 | `<P>-D###` | `docs/<module>/decisions.md` 1장 |
| 미결 | `<P>-U##` | `docs/<module>/decisions.md` 2장 |
| 테스트(요구사항·합격 기준) | `<P>-T###` | `docs/<module>/test-spec.md` 1장 |
| 작업 | `<P>-W##` | `docs/<module>/PROGRESS.md` 1장 |
| 공통 결정 | `G-D###` | `docs/policy.md` 3장 |
| 공통 미결 | `G-U##` | `docs/policy.md` 4장 |

형식 예(정의가 아니라 모양만 보이는 예시):

```text
MB-D001  MB-U01  MB-T001  MB-W01  G-D001  G-U01
```

- `<P>`는 1장 표의 모듈 접두어다. 공통 접두어 `G`는 결정·미결에만 쓴다.
- global 모듈은 테스트·작업에 `GL-T###`·`GL-W##`을 쓰고, 결정·미결은 `docs/<module>/decisions.md`가 아니라 `G-D`·`G-U`(`docs/policy.md`)에 둔다([G-D002](policy.md#3-공통-결정-이력)).
- Step 기록 문서(`docs/testing/step*.md`, `backend/README.md` 9장)의 결정 번호(`Q1`…)는 그 문서 안에서 매긴다. 다른 문서에서는
  문서 링크와 함께 "step4 Q4"처럼 참조한다. 모듈·공통 정책 결정에는 위 접두 ID를 쓴다.

## 4. 작성 규칙

- **ID 정의**: 원본 표의 첫 칸에 ID를 쓴다(`| <P>-D### | … |` 모양). 다른 곳에서는 ID만 적거나 원본으로 링크한다. 같은 ID를 두 곳에
  정의하지 않는다.
- **표지**: 아직 정하지 않은 내용은 `- 미정 (결정 전)`, 구현 전이라 적을 것이 없는 내용은 `- 해당 없음 (구현 전)`을 절의 유일한
  내용으로 둔다. 내용을 쓰게 되면 표지를 지운다. 표지와 실제 내용을 한 절에 섞지 않는다.
- **사실만**: 저장소에서 확인한 사실만 적는다. 미확정 정책은 미결로 두고, 구현하지 않은 기능을 완료로, 실행하지 않은 테스트를
  결과로 적지 않는다.
- **결정 기록**: 결정을 바꿀 때는 기존 행을 지우지 않고 상태를 바꾼 뒤 새 ID를 만든다. 미결이 확정되면 미결 행 상태를 `확정
  (<새 결정 ID>)`로 바꾼다.
- **링크**: 저장소 안 문서는 상대 경로로 링크한다. 모듈 문서 첫머리에는 이 절로 가는 링크를 둔다.
- **문서 구조**: 모듈 문서 7종과 모듈 `AGENTS.md`의 필수 절은 각 파일의 `## N.` 절을 그대로 유지한다. 절을 더할 때는 번호를 이어서
  붙인다.

## 5. 읽는 순서

1. 이 문서 1장에서 대상 모듈을 정한다.
2. 모듈 `AGENTS.md`와 `docs/<module>/INDEX.md`를 연다.
3. 모듈 INDEX 3장 "작업 유형별 문서"에서 지금 작업의 행만 보고, 그 문서의 필요한 절만 ID로 찾아 읽는다.
4. 다른 모듈과 연결되면 그 모듈 `module.md`의 "공개 API" 절만 읽는다.
5. 문서끼리 또는 문서와 코드가 다르면 고치기 전에 보고한다.
