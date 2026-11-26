# FE-02: Frontend CI 구축 + Backend·Frontend CI 독립성

- Issue: #16 · 브랜치: `feature/16-frontend-ci-split` · 기준 커밋: `origin/main` `b236ffd`
- 작성일: 2026-11-26
- 상태: **구현·로컬 검증(L1·L2) 완료, GitHub 검증(L3·L4) 전.** Ruleset 필수 체크는 아직 `backend-build`이다
- 범위: Backend·Frontend CI Workflow 분리, 변경 감지와 조건부 build, 최종 Gate, main Ruleset 필수 체크 전환과 검증.
  Frontend 전용 결정은 [frontend decisions.md](../frontend/decisions.md)(FE-D017·FE-D018), Frontend 전용 합격 기준은
  [frontend test-spec.md](../frontend/test-spec.md)(FE-T018)에 둔다. 기존 Backend CI 결정은 [Step 4 문서](step4-ci-coderabbit.md) 1장이 원본이다

## 1. 결정 사항

2026-11-26 사용자 결정(FE-02 정책 인터뷰 1~3차). 이 문서의 ID는 이 문서 안에서 매긴 번호이고, "출처"는 정책 인터뷰 라운드다.

| ID | 결정 | 출처 |
| --- | --- | --- |
| Q1 | Backend·Frontend Workflow를 분리한다(`backend-ci.yml`·`frontend-ci.yml`). 각 Workflow는 변경 감지 Job, 조건부 build Job, 최종 Gate Job(`backend-ci`·`frontend-ci`)을 둔다. Workflow에 `paths` 필터를 두지 않는다. Gate는 정상 생략, failure, cancelled, 비정상 skipped를 구분한다. 감지에 실패하면 build를 실제로 실행하고(Fallback), Fallback 성공과 일반 성공을 로그에서 구분한다 | 1차 Q2·보완 |
| Q2 | 변경 감지는 공통 git diff 스크립트 하나로 한다. base·head SHA와 merge-base를 쓰고 충분한 history를 받는다. `--no-renames`, `-z`로 삭제·이동을 모두 잡는다. Git이 실패하면 양쪽을 실행한다. main push와 `workflow_dispatch`는 전체 실행이다. PR 입력값(제목·브랜치 이름 등)을 Shell에 직접 넣지 않는다. 분류 규칙을 Backend·Frontend에 중복해 두지 않는다 | 1차 Q3·보완 |
| Q3 | 경로 분류: `backend/**`·`backend-ci.yml` → Backend. `frontend/**`·`frontend-ci.yml` → Frontend. 공통 감지 스크립트, 그 밖의 공통 CI 설정, `.gitignore`, `.env.example`, `.coderabbit.yaml`, 공용 API 계약 → 양쪽. `docs/**`·루트 `*.md` → 생략. 분류되지 않은 경로 → 양쪽. API 계약 경로는 임의로 확정하지 않는다. 규칙의 우선순위와 예외는 구현 계획에서 제시한다(3장) | 1차 Q4·보완 |
| Q4 | 공통 CI 결정·검증의 원본은 이 문서다. Frontend 전용 결정은 `FE-D017`부터 frontend decisions.md에 두고 공통 본문을 중복하지 않는다. FE-U07은 Frontend CI(확정, 이 문서)와 E2E(미결, FE-U09)로 나눈다 | 1차 Q6·보완 |
| Q5 | FE-02 PR에서 Gate Job이 생성된 것을 확인한 뒤 사용자가 Ruleset을 바꾼다. 기존 설정을 백업하고, 필수 체크를 `backend-build`에서 Gate 2개로 바꾸고, 의도적 실패 → 차단 → 되돌리기 → 차단 해제를 확인하고, 복구 절차를 둔다. Merge 뒤 main push·`workflow_dispatch`를 검증한다. 전환 기간에는 다른 PR의 생성·Merge를 보류한다(BE-03 개발은 계속). Merge 뒤 BE-03 브랜치 갱신 절차를 검토한다. GitHub Settings 변경은 사용자가 한다 | 1차 Q7·보완 |
| Q6 | 감지 스크립트는 Python 3 표준 라이브러리로 쓰고, 로컬 composite action으로 감싸고, unittest로 검증한다. 판정: unittest 실패 → Gate FAIL(Fallback 성공으로 덮지 않음). git diff·merge-base 실패 → 양쪽 Fallback build. 빈 변경 목록 → 양쪽 Fallback. 정상 미변경 → 정상 생략. 감지 Job 취소 → 차단. 결과 누락·형식 오류 → 정상 생략으로 처리하지 않음. Fallback은 build를 실제로 실행해야 하며, 실행하지 않은 build를 성공으로 처리하지 않는다 | 2차 Q1·보완 |
| Q7 | 빈 변경 목록은 양쪽 Fallback이다. `docs/api/**`는 양쪽을 실행한다(계약 형식·원본 경로는 미결로 둔다) | 2차 Q2 |
| Q8 | 운영 코드에 실패 스위치를 두지 않는다. 실제 git 감지 실패는 임시 Workflow 커밋으로 재현해 Fallback·Gate를 확인한 뒤 되돌린다. PR은 전체 diff를 보므로 FE-02 PR 안에서는 Backend만·Frontend만 바뀐 경우를 검증할 수 없다. 그래서 검증을 L1 단위 테스트, L2 임시 Git 저장소, L3 FE-02 PR 내부, L4 Merge 뒤 별도 검증 PR로 나눈다 | 2차 Q3·보완 |
| Q9 | 공통 CI 시나리오는 이 문서(4장)에, Frontend 전용 요구사항은 FE-T018에 둔다 | 2차 Q5 |
| Q10 | Ruleset 전환 직전에 PR #15 상태를 확인한다. 아직 병합되지 않았으면 전환부터 FE-02 Merge와 전환 검증이 끝날 때까지 #15의 Merge를 보류한다(개발·리뷰 대응은 계속). Merge 뒤 필요하면 `git merge origin/main` 또는 GitHub Update branch(merge)로 갱신한다. Rebase·Force Push는 하지 않는다 | 2차 Q6·보완 |
| Q11 | L4 검증: V1~V5는 FE-02 Issue 번호로 만든 임시 브랜치(`feature/<번호>-ci-verify-<시나리오>`)에서 하고, 검증 뒤 Merge하지 않고 Close한다. 원격 브랜치 삭제는 사용자가 한다. V6은 docs-only 검증과 최종 결과 기록을 겸하는 PR이며 실제로 Merge한다. Issue 자동 종료를 막기 위해 구현 PR과 V1~V5는 `Refs #번호`, V6만 `Closes #번호`를 쓴다. V6 Merge 결과처럼 병합 뒤 결과는 Issue 댓글 등 별도로 기록한다. V5(빈 diff)는 임시 변경 상태로 PR을 만든 뒤 되돌리기 Commit·Push를 하고 검증한다. GitHub 제약으로 실제 검증이 불가능하면 단위 테스트 결과와 GitHub 결과를 구분해 기록한다 | 3차 Q1·보완 |
| Q12 | Gate 표시는 NORMAL·SKIPPED·FALLBACK(감지 실패·빈 diff)·FULL(main push·`workflow_dispatch`)·FAILED다. Fallback 사유는 warning annotation과 Step Summary에 남긴다. Fallback build가 실제로 성공했을 때만 PASS다. unittest 실패·감지 Job 취소·결과 누락·형식 오류는 Fallback 성공으로 덮지 않는다 | 3차 Q3·보완 |

## 2. CI 구성

| 항목 | Backend CI (`backend-ci.yml`) | Frontend CI (`frontend-ci.yml`) |
| --- | --- | --- |
| 트리거 | `pull_request`(→ main), `push`(main), `workflow_dispatch`. paths 필터 없음 | 같음 |
| 권한 | 워크플로 전체 `contents: read`만. secret을 쓰지 않는다 | 같음 |
| Job | `backend-changes`(5분) → `backend-build`(20분, `backend == 'run'`일 때) → `backend-ci`(Gate, 5분, `if: always()`) | `frontend-changes`(5분) → `frontend-build`(15분, `frontend == 'run'`일 때) → `frontend-ci`(Gate, 5분, `if: always()`) |
| 동시 실행 | 그룹 `backend-ci-<PR 번호 또는 ref>`. PR은 새 커밋이 오면 이전 실행 취소, main push는 취소하지 않음 | 그룹 `frontend-ci-…`, 같은 규칙 |
| build | Step 4 그대로: `./gradlew clean build --stacktrace`, `test-reports`(14일, 취소 때만 생략)·`backend-war`(성공 때만, 7일) | `npm ci` → lint → typecheck → test → build → 차단 audit → 전체 audit(비차단). Artifact 없음(FE-D018) |
| 캐시 | setup-gradle `cache-provider: basic`(Step 4 그대로) | setup-node `cache: npm`, `cache-dependency-path: frontend/package-lock.json`, `package-manager-cache: false`. npm 전역 캐시만 쓰고 `node_modules`는 캐시하지 않는다. `npm ci`는 매번 실행 |

- `backend-build` Job 이름은 Step 4 그대로 둔다. Ruleset 전환 전의 보호와 복구 절차(5장)가 이 이름을 쓴다. Backend 변경이
  없어 build를 건너뛴 실행에서는 Artifact가 생기지 않는다(실행하지 않은 build이므로 Step 4 Q2의 변경이 아니다).
- 공통 스크립트(Python 3 표준 라이브러리, 러너 기본 `python3`): `.github/ci/detect_changes.py`(분류·감지),
  `gate.py`(Gate 판정), `npm_audit_report.py`(audit 판정, FE-D017·FE-D019), `run_tests.py`(unittest 실행기, 0건·실패·import 오류는
  실패), `tests/`(unittest). 로컬 composite action: `.github/actions/detect-changes`(unittest → 감지),
  `.github/actions/ci-gate`(Gate 판정).
- PR에서 온 값은 `with:`·`env:`로만 넘기고 `run:` 본문에 `${{ }}`를 넣지 않는다. 감지 결과는 고정 토큰만 출력하고, 파일 경로는
  Job 로그에 `repr`로만 남긴다(줄 머리에 고정 문자열을 둬 워크플로 명령으로 해석되지 않게 한다). unittest 출력은 버퍼링한다.

### 2.1 감지 출력과 허용 조합

| mode | reason | backend / frontend | 언제 |
| --- | --- | --- | --- |
| NORMAL | `changed` | 해당 쪽만 run(둘 다 skip 불가) | PR 변경이 분류됨 |
| NORMAL | `docs_only` | skip / skip | PR 변경이 모두 "생략" |
| FALLBACK | `git_error` | run / run | `git merge-base`·`git diff` 실패, git 실행 불가 |
| FALLBACK | `empty_diff` | run / run | merge-base 기준 변경 목록이 비어 있음 |
| FALLBACK | `invalid_input` | run / run | base·head SHA가 40자리 소문자 16진수가 아님 |
| FALLBACK | `unknown_event` | run / run | 위 세 트리거 밖의 이벤트 |
| FULL | `push_main` / `dispatch` | run / run | main push / 수동 실행 |

### 2.2 Gate 판정표

각 Gate는 BE·FE 양쪽 감지 출력이 2.1의 조합인지 먼저 확인한 뒤 자기 영역의 build 결과를 판정한다.

| # | 변경 감지 Job | 감지 출력 | 자기 영역 build | 판정 |
| --- | --- | --- | --- | --- |
| 1 | success가 아님(failure: unittest 실패·0건·예외, cancelled, skipped) | — | — | FAILED |
| 2 | success | 출력 누락·허용 밖 토큰·2.1에 없는 조합(예: NORMAL·changed·skip/skip) | — | FAILED |
| 3 | success | 자기 영역 skip | skipped | **SKIPPED · PASS** |
| 4 | success | 자기 영역 skip | skipped 아님 | FAILED |
| 5 | success | 자기 영역 run | success | **NORMAL / FALLBACK / FULL · PASS** |
| 6 | success | 자기 영역 run | failure·cancelled·skipped·알 수 없음 | FAILED |

- FALLBACK이면 warning annotation과 Step Summary에 사유를 남긴다. FAILED면 error annotation을 남긴다.
- Gate는 `if: always()`라 감지·build가 실패·취소돼도 실행된다(건너뛴 Job은 필수 체크에서 통과로 취급되므로 Gate를 건너뛰지 않는다).
  Workflow 파일 오류로 실행이 아예 없으면 필수 체크가 생기지 않아 병합이 막힌다.

### 2.3 Action 버전

| Action | 태그 | 커밋 SHA | 공개일(UTC) | 확인 출처 |
| --- | --- | --- | --- | --- |
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | 2026-07-20 | Step 4 2.1, Releases API(이후 릴리스 없음) |
| actions/setup-node | v7.1.0 | `949feb2413d6458794dcd2491c4babbbce0c15c1` (lightweight 태그) | 2026-10-08 | Releases API `published_at`, `git/ref/tags/v7.1.0`, `action.yml`(`runs.using: node24`, `cache`·`cache-dependency-path`·`package-manager-cache` 입력) |
| actions/setup-java · gradle/actions/setup-gradle · actions/upload-artifact | Step 4 2.1과 같음 | 같음 | 같음 | 바꾸지 않음 |

- 기준일 2026-11-26에 모두 공개 14일이 지났다. Commit 직전에 태그 → SHA를 다시 확인한다.
- 로컬 action(`./.github/actions/*`)은 SHA 고정 대상이 아니다. 저장소 소유자 허용 조건으로 실행되는지는 L3-1에서 확인한다.
- 명시적 `cache: npm`과 `package-manager-cache: false`를 함께 쓸 때의 동작은 setup-node 문서에 적혀 있지 않다. L3-1·L3-2 로그로
  저장·복원을 확인한다.
- Workflow·action 파일은 SchemaStore `github-workflow.json`·`github-action.json`(draft-07, 카탈로그 "GitHub Workflow"·"GitHub Action"
  항목)으로 검증한다(보조 검증. PR에서는 CodeRabbit actionlint가 따로 검사한다).

## 3. 변경 감지 규칙의 우선순위·예외

위에서부터 처음 맞는 규칙을 쓴다(구현: `.github/ci/detect_changes.py` `classify`).

| 순위 | 경로 | 분류 |
| --- | --- | --- |
| 1 | `.github/workflows/backend-ci.yml` | Backend |
| 2 | `.github/workflows/frontend-ci.yml` | Frontend |
| 3 | 그 밖의 `.github/**`(감지·Gate 스크립트, composite action, 기타 CI 설정) | 양쪽 |
| 4 | `backend/**` | Backend |
| 5 | `frontend/**`(`frontend/.gitignore`·`frontend/AGENTS.md` 포함) | Frontend |
| 6 | `docs/api/**` | 양쪽 |
| 7 | 그 밖의 `docs/**` | 생략 |
| 8 | 루트 `*.md`(`/` 없는 경로, 확장자 소문자) | 생략 |
| 9 | 루트 `.gitignore`·`.env.example`·`.coderabbit.yaml` | 양쪽 |
| 10 | 나머지(미분류) | 양쪽 |

- 판정: 변경 목록에 Backend 또는 양쪽 분류가 하나라도 있으면 Backend 실행, Frontend도 같다. 목록이 비어 있지 않은데 모두
  "생략"이면 양쪽 정상 생략(`docs_only`).
- 이동은 `--no-renames`로 삭제 + 추가로 보이므로 원래 경로와 새 경로가 각각 분류된다(예: `backend/` → `frontend/` 이동은 양쪽).
- 경로는 `git diff -z` 출력을 NUL로 나눠 UTF-8(surrogateescape)로 읽는다. 공백·개행·한글 이름도 같은 규칙을 따른다.

## 4. 검증 계획 (L1~L4, V1~V6)

| 단계 | 내용 | 합격 기준 |
| --- | --- | --- |
| L1 단위 | `.github/ci/tests`: 분류 규칙 1~10·우선순위 충돌·공통 설정·미분류·`docs/api/**`, `-z` 파싱, 감지 결과가 2.1 조합 안인지, Gate 곱집합(영역 2 × 감지 Job 5 × BE 4 × FE 4 × mode 6 × reason 10 × build 5) 대 독립 참조표, audit 판정(필수 필드 누락·잘못된 값·종료 코드 불일치 = AUDIT_ERROR), `run_tests` 0건 = 실패. Schema 검증 | `python -B -I .github/ci/run_tests.py` exit 0, Schema 오류 0 |
| L2 임시 Git 저장소 | 같은 테스트 안에서 실제 git으로 BE만, FE만, docs만, 여러 커밋, 변경 뒤 되돌림(빈 diff), 삭제, 경계 넘는 이동, 같은 영역 안 이동, 특수 이름, 빈 커밋, 없는 40자리 SHA(git exit 128), 연관 없는 이력, 분기 뒤 base만 바뀐 경우 | 2.1의 기대 조합과 같음 |
| L3-1 FE-02 PR 첫 Push | 두 Workflow 실행 | 양쪽 NORMAL, build 실제 실행, Gate 2개 생성·PASS, `backend-build`·Artifact 2종 유지, 로컬 action·SHA 강제 설정 통과, npm 캐시 저장(`Cache saved`, 키에 lockfile 해시) |
| L3-2 unittest 실패(임시 커밋 → 되돌리기) | `.github/ci/tests`의 단언 1개를 틀리게 바꿈 | 두 감지 Job 실패 → Gate 2개 FAILED → 되돌린 뒤 PASS. 되돌린 뒤 실행에서 npm 캐시 복원 확인(`Cache restored`) |
| L3-3 감지 실패(임시 Workflow 커밋 → 되돌리기) | 두 Workflow의 감지 단계 `base-sha`를 없는 40자리 SHA 리터럴로 바꿈(운영 코드에 스위치 없음) | 형식 검사 통과 뒤 실제 `git merge-base` 실패 → FALLBACK(`git_error`), warning, build 실제 실행 → PASS. 되돌린 뒤 NORMAL |
| L3-4a BE만 실패(Ruleset 전환 뒤, 임시 커밋 → 되돌리기) | Backend 테스트 1개를 실패시킴 | `backend-ci` FAILED, `frontend-ci` PASS, 병합 차단 → 되돌린 뒤 둘 다 PASS, 차단 해제 |
| L3-4b FE만 실패(임시 커밋 → 되돌리기) | Frontend 테스트 1개를 실패시킴 | `backend-ci` PASS, `frontend-ci` FAILED, 병합 차단 → 되돌린 뒤 둘 다 PASS, 차단 해제 |
| L4-0 Merge 뒤 | push main, 두 Workflow `workflow_dispatch` | 양쪽 FULL PASS |
| V1 BE만 | `backend` 주석 1줄 | `backend-ci` NORMAL PASS(build 실행), `frontend-ci` SKIPPED PASS, 불필요한 차단 없음 → Close |
| V2 FE만 | `frontend` 주석 1줄 | V1의 반대 → Close |
| V3 여러 커밋 | 커밋 1 BE, 커밋 2 FE. 커밋 2를 커밋 1 실행이 끝나기 전에 Push | 양쪽 NORMAL PASS(merge-base 기준 전체 diff). 연속 Push로 이전 실행이 `cancelled`되고 최신 커밋의 Gate만 판정에 쓰임(concurrency). 취소된 이전 실행의 Gate는 `if: always()`로 이전 SHA에 FAILED를 남길 수 있으며 병합 판정(최신 head SHA)과 무관하므로 오판하지 않는다. 이전 실행이 먼저 끝나 확인하지 못하면 "미검증"으로 기록하고 별도 확인을 제안 → Close |
| V4 이동·삭제 | build에 영향 없는 파일의 이동·삭제(대상은 실행 시점에 경로·영향을 보이고 확정) | 원래·새 경로가 각각 분류된 대로 NORMAL PASS → Close |
| V5 빈 diff | 임시 변경으로 PR 생성 → 되돌리기 Commit·Push | 양쪽 FALLBACK(`empty_diff`), build 실행 → PASS → Close |
| V6 docs만 + 결과 기록 | 이 문서 6장에 L3·L4 결과 기록 | 양쪽 SKIPPED PASS → 사용자 Merge(`Closes #16`). Merge 뒤 결과는 Issue #16 댓글 |

- 브랜치: `feature/16-ci-verify-<be-only|fe-only|multi-commit|move-delete|empty-diff|docs-record>`(Merge 뒤 `origin/main`에서 생성).
  구현 PR과 V1~V5는 `Refs #16`, V6만 `Closes #16`. 원격 브랜치 삭제는 사용자가 한다.
- Commit·Push는 1회마다 승인한다(3차 Q2). 공통 설정·미분류 경로는 L1·L2로 검증한다.
- GitHub 제약으로 실검증하지 못한 항목은 단위 테스트 결과와 구분해 6장에 적는다.

## 5. GitHub Settings 변경과 Ruleset 전환·복구 절차

GitHub 설정은 사용자가 웹 UI에서 한다(Step 4 Q6). Claude Code는 공개 API로 읽기만 한다.

| 순서 | 시점 | 작업 | 확인 |
| --- | --- | --- | --- |
| G1 | 첫 Push 전 | `Settings → Actions → General` 허용 목록에 `actions/setup-node@*` 추가. 기존 4개·"full-length commit SHA" 강제는 그대로 | L3-1에서 setup-node·로컬 action 실행 |
| G2 | L3-1~L3-3 PASS 뒤 | 열린 PR 확인(PR #15는 2026-11-26 `3b0f46a`로 이미 병합) | 공개 API |
| G3 | 전환 직전 | 사용자: Rulesets → `main-protection` → Export(JSON, 저장소 밖 보관). Claude: `GET /repos/<owner>/<repo>/rulesets/28061569` 값을 6장에 기록 | 백업 파일·기록 |
| G4 | G3 뒤 | 필수 체크에 `backend-ci`·`frontend-ci`(소스 GitHub Actions) 추가, `backend-build` 제거. strict 끔·Bypass 없음·나머지 규칙 그대로 | — |
| G5 | G4 직후 | 공개 API로 필수 체크 2개·`integration_id: 15368` 확인 | 6장 기록 |
| G6~G8 | — | L3-4a·L3-4b → 사용자 Squash Merge → L4-0 → V1~V6 | 4장 |

- 전환(G4)부터 FE-02 Merge와 L4-0 완료까지 다른 PR의 생성·Merge를 보류한다(Q5). 이미 열린 PR은 개발·리뷰 대응을 계속하되
  Merge만 보류한다(Q10). 그 뒤 기존 PR은
  `git merge origin/main` 또는 Update branch(merge)로 갱신해 Gate Workflow를 받는다(Rebase·Force Push 금지).
- **R1 복구(FE-02 Merge 전)**: 필수 체크를 `backend-build` 하나로 되돌리고(Import 또는 수동) API로 확인한다. FE-02 PR에는
  `backend-build` Job이 남아 있다. 원인을 고친 뒤 G3부터 다시 한다.
- **R2 복구(Merge 뒤)**: 같은 방법으로 되돌리고 API로 확인한다. 이 동안 Frontend만 바뀐 PR은 `backend-build`가 건너뛰어진 것이
  통과로 처리돼 보호가 약해지므로 **일반 PR Merge를 모두 보류한다**. CI 수정 PR은 BE·FE 실제 검증 결과(Gate 실행, BE만·FE만
  의도적 실패와 해제)를 확인한 뒤 처리하고, 신규 Gate로 다시 전환(G3~G5)·검증을 마친 뒤 일반 PR Merge를 재개한다.
- 복구 사유: Gate 오판정, 체크 이름을 찾지 못해 "Expected — Waiting"으로 멈춤, 모든 PR 차단, 사용자 판단. Bypass 추가·Ruleset
  비활성화·Force Push는 하지 않는다. 모든 단계는 6장에 기록한다.

## 6. 실행 기록

| 일시 | 검증 | 결과 |
| --- | --- | --- |
| 2026-11-26 | L1·L2: `python -B -I .github/ci/run_tests.py`(Windows, Python 3.12.4, git 2.50.0) | exit 0, 96건 통과(Gate 곱집합 48,000 조합은 1건 안에서 검사). `__pycache__` 생성 0 |
| 2026-11-26 | L1 변이 확인(스크래치 사본): `backend/**` 규칙 삭제, `backend/**`→양쪽, `--no-renames` 제거, NORMAL·changed·skip/skip 허용, 0건 성공 처리, AUDIT_ERROR 통과 처리, total 검사 제거 | 7종 모두 테스트 실패(각각 6·6·2·2·1·3·1건) |
| 2026-11-26 | L1 Schema: SchemaStore `github-workflow.json`·`github-action.json`(2026-11-26 내려받음)으로 `.github/workflows/*.yml` 2개·`.github/actions/*/action.yml` 2개 검증 | 오류 0(PyYAML이 `on` 키를 불리언으로 읽는 점은 보정). 외부 Action 전부 SHA 고정, `run:` 본문 `${{ }}` 0건 |
| 2026-11-26 | FE-T001 명령·audit 판정(로컬) | [frontend regression-log](../frontend/regression-log.md) 2026-11-26 FE-02 절 |
| 2026-11-26 | `backend/`에서 `.\gradlew.bat :app:build`(clean 없음, 기준 b236ffd + 작업 트리) | BUILD SUCCESSFUL, app 테스트 33건 통과(실패·오류·건너뜀 0) |
| 2026-11-26 | 커밋 직전 태그 → 커밋 SHA 재확인(GitHub `git/ref/tags` API, annotated 태그는 tag 객체까지) | 5개 Action 모두 2.3·Step 4 2.1의 SHA와 같음 |
| — | L3·L4·V1~V6, Ruleset 전환 | 미실행(PR 이후) |
