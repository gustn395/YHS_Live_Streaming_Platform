# Step 4: GitHub Actions CI + CodeRabbit + main 보호

- 브랜치: `feature/infra-github-actions-ci` → [PR #4](https://github.com/gustn395/YHS_Live_Streaming_Platform/pull/4), main에 Squash Merge(`9c1166e`)
- 작성일: 2026-11-25
- 상태: **구현·주요 검증 완료.** PR CI, CodeRabbit 자동 리뷰, Ruleset, 실패·복구 검증(Q5), Squash Merge, 병합 후 `push(main)`·`workflow_dispatch` 검증까지 확인 (6장 기록, 6.2 판정). 일부 세부 증거는 미확인이며 6.3에 모았다
- 범위: CI만. AWS·ECR·EC2·Docker Compose 배포·GitHub OIDC·CD Workflow는 제외(Streaming Phase 4 실기능 구현과 최초 수동 AWS 배포 검증 이후 별도 단계)

## 1. 결정 사항

2026-11-25 사용자 결정 (정책 인터뷰 Q1~Q7).

| ID | 결정 | 이유 |
| --- | --- | --- |
| Q1 | Action은 커밋 SHA로 고정하고 태그를 주석으로 남긴다. 출시 후 14일 이상 지난 안정 버전만 쓴다 | 태그 변조·갓 나온 버전의 결함을 피한다. 그래서 setup-java는 최신 v6.3.0(11-14) 대신 v6.2.0을 쓴다 |
| Q2 | 성공한 모든 빌드(PR·main push·수동 실행)에서 실행 가능한 WAR만 올리고 7일 보관한다. plain WAR는 올리지 않는다 | PR 산출물을 바로 받아 볼 수 있게 하고, 아직 쓰지 않는 외부 Tomcat용 WAR는 뺀다 |
| Q3 | main Ruleset: Bypass 없음(관리자 포함), 브랜치 최신화 강제(strict) 끔, 필수 승인 0명 | 1인 개발이라 본인 PR을 승인할 수 없다. 규칙 우회 경로를 두지 않는다 |
| Q4 | `.coderabbit.yaml`에는 Spring Boot 버전 호환 충돌 방지 지시만 둔다. 정책 원문은 이후 AGENTS.md에 둔다 | 리뷰가 개발 원칙과 어긋나지 않게 하면서 정책 원본을 한 곳에 둔다 |
| Q5 | 같은 PR에서 실패 테스트 커밋 → CI 실패·병합 차단 확인 → 되돌리기 커밋 → CI 성공·차단 해제 확인 | CI 실패와 병합 차단의 관계를 실제로 확인한다. Squash 병합이므로 main 이력에는 남지 않는다 |
| Q6 | GitHub 설정(Actions 권한, CodeRabbit 설치, Merge 방식, Ruleset)은 사용자가 웹 UI에서 직접 한다. Claude Code는 체크리스트(5장)만 제공한다 | 이 PC에 `gh` CLI가 없고, 저장소 설정 변경은 사용자가 직접 통제한다 |
| Q7 | Actions 권한은 선택 허용 방식으로, 워크플로가 쓰는 Action 4개만 `@*`로 허용하고 전체 SHA 고정을 강제한다(5.1) | 필요한 Action만 실행되게 하고, Q1의 SHA 고정을 설정으로도 강제한다. 버전을 올릴 때는 설정을 바꿀 필요가 없다 |

추가 기술 결정 (사용자 이견 없음):

- setup-gradle 캐시는 `cache-provider: basic`(MIT 오픈소스)을 쓴다. 기본값 `enhanced`는 상용 라이브러리다.
- CodeRabbit은 자동 리뷰만 한다. 필수 승인·필수 체크로 지정하지 않는다(후속 단계에서 검토).

## 2. CI 구성 (`.github/workflows/backend-ci.yml`)

| 항목 | 값 |
| --- | --- |
| 트리거 | `pull_request`(→ main), `push`(main), `workflow_dispatch`. **paths 필터 없음**. `workflow_dispatch`(수동 실행)는 기본 브랜치(main)에 이 워크플로가 있어야 실행할 수 있다([GitHub 문서](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)). 이 워크플로를 처음 추가하는 PR #4가 병합되기 전에는 쓸 수 없다 |
| 권한 | 워크플로 전체 `contents: read`만 |
| Runner | `ubuntu-24.04`, `timeout-minutes: 20` |
| 필수 체크 이름 | Job 이름 `backend-build` (Ruleset에 이 이름으로 등록) |
| 동시 실행 | 같은 PR은 새 커밋이 오면 이전 실행 취소. main push는 취소하지 않음 |
| 빌드 | `backend/`에서 `./gradlew clean build --stacktrace` (app + 도메인 모듈 7개, build-logic은 included build) |
| Wrapper 검증 | setup-gradle `validate-wrappers: true`(jar 체크섬) + `gradle-wrapper.properties`의 `distributionSha256Sum`(배포본) |
| 테스트 실패 | `build`가 `test`를 포함하므로 실패 시 Gradle 종료 코드가 0이 아니고 Job이 실패한다 |
| 테스트 리포트 | `test-reports`: `**/build/reports/tests/`, `**/build/test-results/`. 취소된 경우만 빼고 항상 업로드, 14일 |
| WAR | `backend-war`: `app/build/libs/*.war`에서 `*-plain.war` 제외. 성공 시만, 7일, 파일이 없으면 실패 |
| 외부 의존성 | 없음. DB·Redis·MediaMTX 서비스 컨테이너를 두지 않는다(Step 3 Q5) |

### 2.1 Action 버전

| Action | 태그 | 커밋 SHA | 출시일 (UTC) |
| --- | --- | --- | --- |
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | 2026-07-20 |
| actions/setup-java | v6.2.0 | `77dd5b2ce225ea11469928ac317850245a74ae78` | 2026-11-04 |
| gradle/actions/setup-gradle | v6.4.1 | `8da46b539cd8fe6ab27ca75128919a18d757604c` (annotated tag가 가리키는 커밋) | 2026-10-28 |
| actions/upload-artifact | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | 2026-10-07 |

버전을 올릴 때는 공식 릴리스의 태그 → 커밋 SHA를 다시 확인하고, 워크플로의 SHA·주석과 이 표를 함께 고친다.

## 3. CodeRabbit 구성 (`.coderabbit.yaml`)

| 항목 | 값 |
| --- | --- |
| 언어 | `ko-KR` |
| 리뷰 강도 | `profile: chill` |
| 자동 리뷰 | 켬. main 대상 PR만, Draft PR 제외 |
| 승인·변경 요청 | `auto_approvals.enabled: false` (Approve·Request changes를 남기지 않음, 3.1 참고) |
| 리뷰 제외 | 빌드 산출물·캐시·바이너리만: `**/build/**`, `**/.gradle/**`, `gradle-wrapper.jar`. `poc/**`는 리뷰 대상이다 |
| 경로별 지시 | Java(보안, 예외 처리, 테스트 누락, Boot 버전 호환), `modules/**`(모듈 간 직접 의존 금지), `*.gradle.kts`(저장소 선언 위치, 버전 관리 위치), `.github/workflows/**`(최소 권한, SHA 고정, 필수 체크 건너뜀 방지) |
| 도구 | actionlint, gitleaks 명시적으로 켬(나머지는 CodeRabbit 기본값) |
| 코딩 지침 | `knowledge_base.code_guidelines` 켬. 기본 파일 패턴에 `**/AGENTS.md`, `**/CLAUDE.md`가 포함된다 |

### 3.1 승인·변경 요청이 켜지지 않는 근거

2026-11-25 CodeRabbit 공식 스키마(`schema.v2.json`)와 문서([Automatic Approvals](https://docs.coderabbit.ai/pr-reviews/automatic-approvals.md), [Choose an approval policy](https://docs.coderabbit.ai/pr-reviews/approval-policies.md))로 확인했다.

- `reviews.request_changes_workflow`는 `reviews.auto_approvals.enabled`의 **레거시 별칭**이다. 동작은 같다. 두 키에 서로 다른 값을 주면 CodeRabbit이 자동 승인을 끄고 설정 오류를 보고하므로 한 키만 쓴다. 이 저장소는 새 키만 쓴다.
- `auto_approvals.enabled`가 켜져 있으면 CodeRabbit이 차단 수준 지적에 **Request changes** 리뷰를 남기고, 조건이 충족되면 **Approve** 리뷰를 남긴다. 꺼져 있으면(기본값 false) 둘 다 하지 않는다. 자동 리뷰(`auto_review.enabled`)와는 별개 설정이라 리뷰 코멘트는 그대로 달린다.
- `@coderabbitai approve`·`resolve` 명령이 승인까지 하는 것은 자동 승인이 켜져 있을 때다. 꺼져 있으면 리뷰 스레드 정리만 한다.
- CodeRabbit은 PR에 `CodeRabbit` 체크(리뷰 진행 상태)를 표시한다. main Ruleset의 필수 체크에는 `backend-build`만 등록하고 `CodeRabbit`은 등록하지 않는다. 필수 승인도 0명이라 CodeRabbit 리뷰 결과는 병합 조건이 아니다.
- CodeRabbit의 자동 병합 기능(Triage 규칙)은 별도 기능이며 이 저장소에서 설정하지 않는다.

## 4. GitHub 저장소 상태 (2026-11-25 확인, 변경 전)

비인증 GitHub API로 확인했다. 인증이 필요한 항목은 미확인이다.

| 항목 | 상태 |
| --- | --- |
| 공개 여부 | public → Free 요금제에서도 Ruleset 사용 가능 |
| main 보호 | 없음 (`protected: false`, Ruleset 0개) |
| Step 3 병합 | PR #3, Merge Commit 방식으로 병합됨 (`6bd371f`) |
| Merge 방식 허용 설정, Actions 권한, CodeRabbit 설치 | 미확인 (5장에서 사용자가 확인) |

### 4.1 적용 후 (2026-11-25 확인)

비인증 API `GET /repos/<owner>/<repo>/rules/branches/main`, `GET /repos/<owner>/<repo>/rulesets/28061569`과 사용자 화면 확인으로 기록했다.

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| Ruleset | `main-protection`(id `28061569`), `enforcement: active`, 대상 `~DEFAULT_BRANCH`(main) | API |
| 적용 규칙 | `deletion`, `non_fast_forward`, `required_linear_history`, `pull_request`, `required_status_checks` | API |
| PR 규칙 | 필수 승인 0, 대화 해결 필수 끔(`required_review_thread_resolution: false`), 병합 방식 `squash`만 | API |
| 필수 체크 | `backend-build` 하나(발신 앱 GitHub Actions, `integration_id: 15368`), strict 끔 | API |
| Bypass 목록 | 비어 있음 | 사용자 화면 확인 (API는 관리자 전용 필드라 응답에 없음) |
| 계획에 없던 값 | `require_extra_approval_for_unattributed_changes: true` | API 관측값. 의미·영향은 확인하지 못했다(설정 오류로 보지 않는다). PR #4는 CI 성공 후 병합할 수 있었다. 설정은 바꾸지 않았다 |
| PR #4 head 브랜치 | 병합 후 원격에서 삭제됨 | `git ls-remote origin refs/heads/feature/infra-github-actions-ci` 결과 없음 |
| 저장소 설정 `Automatically delete head branches` | 미확인 | 위 삭제가 이 설정에 의한 것인지 화면으로 확인하지 않았다. 6.3 참고 |
| 저장소 Merge 방식 체크박스, Squash 기본 메시지, Actions 권한(5.1) | 미확인 | 비인증 API 응답에 필드가 없음. 6.3 참고 |

## 5. GitHub 웹 UI 설정 체크리스트 (사용자 수행)

메뉴 이름은 2026-11 기준 GitHub UI를 가정했다. 이름이 다르면 같은 의미의 항목을 고르고 6장에 기록한다.

### 5.1 Actions 권한 — PR을 올리기 전

`Settings → Actions → General`

- [ ] **Actions permissions**: `Allow <owner>, and select non-<owner>, actions and reusable workflows`를 고르고 아래 설정을 적용한다 (Q7)

  두 방식의 차이 (참고):

  | 방식 | 허용 범위 |
  | --- | --- |
  | `Allow all actions and reusable workflows` | 어떤 공개 Action이든 실행된다. 워크플로를 바꾸는 PR이 새 Action을 넣으면 그대로 실행된다 |
  | `Allow <owner>, and select non-<owner>, actions and reusable workflows` | 저장소 소유자의 Action과 아래 체크·목록에 있는 Action만 실행된다. 목록에 없으면 해당 Job이 실행 전에 실패한다 |

  선택 허용 방식에서 이 워크플로에 필요한 Action만 허용하는 설정:
  - `Allow actions created by GitHub`: 끔 (켜면 `actions/*`, `github/*` 전체가 허용된다)
  - `Allow actions by Marketplace verified creators`: 끔
  - `Allow or block specified actions and reusable workflows`:
    `actions/checkout@*, actions/setup-java@*, actions/upload-artifact@*, gradle/actions/setup-gradle@*`
  - `Require actions to be pinned to a full-length commit SHA`: 켬 (현재 워크플로는 4개 모두 SHA 고정이라 바로 통과한다)
  - 4개 Action은 모두 `node24` JavaScript Action이라 내부에서 다른 Action을 호출하지 않는다. Action을 추가하거나 바꾸면 이 목록도 함께 고친다.
  - 하위 경로 Action(`gradle/actions/setup-gradle@*`) 표기는 GitHub 문서 예시에 직접 없다. 첫 CI 실행에서 차단되면 `gradle/actions/*`로 바꾸고 6장에 기록한다.
- [ ] **Workflow permissions**: `Read repository contents and packages permissions`
- [ ] **Allow GitHub Actions to create and approve pull requests**: 끔
- 확인: 저장소 `Actions` 탭이 열리고, 이 브랜치를 Push한 뒤 `Backend CI` 워크플로가 보인다.

### 5.2 CodeRabbit 설치 — PR을 열기 전

- [ ] https://github.com/apps/coderabbitai 에서 `Install`(이미 설치돼 있으면 `Configure`)
- [ ] 계정 선택 → `Only select repositories` → 이 저장소만 선택 → `Install`
- [ ] 요청되는 권한 목록을 확인하고 승인. 필요하면 https://app.coderabbit.ai 에 GitHub 계정으로 로그인
- 확인: `Settings → GitHub Apps`(또는 `Integrations → Applications`)에 CodeRabbit이 있다. PR을 열면 CodeRabbit 요약·리뷰 코멘트가 달린다.

### 5.3 Merge 방식 — Ruleset 전후 상관없음

`Settings → General → Pull Requests`

- [ ] `Allow merge commits`: 끔
- [ ] `Allow squash merging`: 켬, 기본 메시지 `Default to pull request title and description`
- [ ] `Allow rebase merging`: 끔
- [ ] `Automatically delete head branches`: 켬
- [ ] `Allow auto-merge`: 끔 (현 단계 미사용)
- 확인: PR 화면의 병합 버튼이 `Squash and merge` 하나만 보인다.

### 5.4 main Ruleset — 이 PR의 첫 CI 실행(`backend-build`)이 끝난 뒤

`Settings → Rules → Rulesets → New ruleset → New branch ruleset`

- [ ] Ruleset name: `main-protection`
- [ ] Enforcement status: `Active`
- [ ] Bypass list: **비워 둔다** (관리자·Repository admin 역할도 추가하지 않음)
- [ ] Target branches: `Add target → Include default branch`
- [ ] `Restrict deletions`: 켬
- [ ] `Require linear history`: 켬
- [ ] `Require a pull request before merging`: 켬
  - Required approvals: `0`
  - Dismiss stale approvals / Require review from Code Owners / Require approval of the most recent reviewable push: 끔
  - **Require conversation resolution before merging: 끔** (켜면 CodeRabbit 코멘트가 병합을 막는다)
  - Allowed merge methods: `Squash`만
- [ ] `Require status checks to pass`: 켬
  - **Require branches to be up to date before merging: 끔** (Q3)
  - `Add checks` → `backend-build` 선택 (소스: GitHub Actions)
- [ ] `Block force pushes`: 켬
- [ ] `Restrict creations`, `Restrict updates`: 끔
- [ ] `Create`
- 확인:
  - Claude Code가 비인증 API `GET /repos/<owner>/<repo>/rules/branches/main`으로 적용된 규칙(deletion, non_fast_forward, required_linear_history, pull_request, required_status_checks)을 조회한다.
  - PR 화면에 `backend-build`가 `Required`로 표시된다.

## 6. 검증 기록

| 일시 (KST) | 검증 | 결과 |
| --- | --- | --- |
| 2026-11-25 | Windows, Gradle 9.9.2(실행 JDK 25.0.4.1) + toolchain JDK 17: `backend/`에서 `./gradlew clean build --stacktrace` | 성공(27초). 모듈 `:app` + `:modules:*` 7개 빌드, 테스트 3개 중 실패 0·오류 0·건너뜀 0(도메인 모듈은 NO-SOURCE). WAR 2개 생성(실행 가능 22,075,028 B, plain 15,484,442 B) |
| 2026-11-25 | `backend-ci.yml`을 SchemaStore `github-workflow.json` 스키마로 검증(Python jsonschema) | 오류 0. 트리거 3개, 권한 `contents: read`, Job 이름 `backend-build` |
| 2026-11-25 | Action 4개: `git ls-remote`로 태그 → 커밋 SHA 대조, GitHub Releases API로 정식 릴리스 여부 확인, 고정 SHA의 `action.yml`에서 사용한 입력값 존재 확인 | 4개 모두 SHA 일치, prerelease 아님. 입력값 모두 존재. 4개 모두 `node24` 런타임 |
| 2026-11-25 | `.coderabbit.yaml`을 CodeRabbit 공식 스키마(`schema.v2.json`)로 검증 | 오류 0. `request_changes_workflow`는 스키마에서 deprecated라 `auto_approvals.enabled`로 대체 |
| 2026-11-25 | actionlint | **미실행** (로컬 미설치, 임의 설치하지 않음). CodeRabbit의 actionlint가 PR에서 대신 검사한다 |
| 2026-11-25 | `git diff --check`, 추가 파일 민감정보 검색 | 이상 없음 |
| 2026-11-25 | `.coderabbit.yaml`에서 `poc/**` 제외 규칙 제거 후 공식 스키마 재검증 | 오류 0 |
| 2026-11-25 | `request_changes_workflow`와 `auto_approvals.enabled` 차이를 공식 스키마·문서로 확인 | 레거시 별칭으로 동작 같음. 3.1에 근거 기록 |
| 2026-11-25 | PR #4 `Backend CI` 첫 실행(`pull_request`, 커밋 `b324229`), 공개 GitHub API로 Job·단계·Artifact 조회 | `backend-build` 성공. 단계 7개(Checkout, JDK 17, Gradle 설정, Build and test, 리포트·WAR 업로드) 모두 성공. Artifact `test-reports`(12,359 B), `backend-war`(19,903,666 B, 실행 가능한 WAR) 생성. **테스트 3개 개별 결과와 wrapper 검증 로그는 미확인**(로그·Artifact 내용을 열어 보지 않음) |
| 2026-11-25 | PR #4 CodeRabbit 자동 리뷰, 공개 GitHub API로 조회 | 요약 코멘트 1개, 리뷰 1건(상태 `COMMENTED`, 승인·변경 요청 아님), 인라인 지적 1개: 이 문서 6.1의 `workflow_dispatch` 검증을 최초 main 병합 뒤로 옮기라는 지적(Minor) → 6.1에 반영 |
| 2026-11-25 | CodeRabbit 지적 반영 후 이 문서만 수정, `git diff --check` | 통과. 변경 파일은 이 문서 하나 |
| 2026-11-25 | 수정 커밋 `b067249` Push 후 PR #4 CI 재실행 ([run 45571903051](https://github.com/gustn395/YHS_Live_Streaming_Platform/actions/runs/45571903051)), CodeRabbit 재리뷰 | `backend-build` 성공, Artifact `test-reports`(12,342 B)·`backend-war`(19,903,666 B). CodeRabbit "No actionable comments", 기존 지적에 "Addressed in commit b067249" 표시 |
| 2026-11-25 | 사용자가 5.3·5.4 적용 → Ruleset을 비인증 API로 조회 | 계획대로 적용(4.1). PR #4 `mergeable_state: clean`(head `b067249`) |
| 2026-11-25 | Q5 실패 테스트(`backend/app/src/test/java/com/yhs/livestreaming/ci/CiFailureVerificationTest.java`, `assertThat(1 + 1).isEqualTo(3)`) 추가 후 로컬 `./gradlew clean build --stacktrace` | 의도대로 실패. `:app:test`에서 4개 실행·1개 실패, 기존 3개 통과. `expected: 3 but was: 2`(테스트 결과 XML). 컴파일 오류 없음 |
| 2026-11-25 18:03 | **Q5 기록 A — 실패 검증.** 커밋 `9e91d31` Push → [run 45581787360](https://github.com/gustn395/YHS_Live_Streaming_Platform/actions/runs/45581787360) (`pull_request`) | `failure`. 실패 단계 `Build and test`, 앞 단계는 모두 성공. 로그(사용자 전달): `CiFailureVerificationTest > intentionallyFailsToVerifyCiBlocksMerge() FAILED`, `AssertionFailedError at CiFailureVerificationTest.java:17`, `4 tests completed, 1 failed`, `Execution failed for task ':app:test'`. `test-reports` 업로드(파일 12개, 16,034 B), `Upload executable WAR` 건너뜀(`backend-war` 없음). 화면(사용자 확인): `backend-build` ✗, `Squash and merge` 비활성화. 비인증 API `mergeable_state`는 `unstable`(아래 행) |
| 2026-11-25 | 기록 A 시점 공개 API와 화면 차이 | 비인증 `GET /pulls/4`는 `mergeable: true`, `mergeable_state: unstable`을 돌려줬지만 화면에서는 병합이 막혔다. 병합 차단 여부는 **화면 기준**으로 판단한다. `unstable`은 API 관측값으로만 기록하고, 원인은 확인하지 못했다 |
| 2026-11-25 18:14 | **Q5 기록 B — 복구 검증.** `git revert --no-commit 9e91d31` → 커밋 `b7b6c54` Push → [run 45583570195](https://github.com/gustn395/YHS_Live_Streaming_Platform/actions/runs/45583570195) (`pull_request`) | 커밋은 임시 테스트 삭제 1건, `git diff b067249 HEAD` 비어 있음(트리 해시 같음). Run `success`, 모든 단계 성공. Artifact `test-reports`(12,349 B)·`backend-war`(19,903,666 B). check run `backend-build` 성공. `mergeable_state: clean`. CodeRabbit 체크 성공, 새 지적 0건. 실행된 테스트 건수는 미확인 |
| 2026-11-25 18:18 | PR #4 Squash Merge (사용자) | main `9c1166e`, 부모 `6bd371f` 하나(단일 부모, Linear History 유지). `git diff b7b6c54 9c1166e` 비어 있음. 병합 전 main 대비 5개 파일(+361), 임시 테스트는 main에 없음. 원격 head 브랜치 삭제됨(설정 확인 여부는 4.1). 커밋 본문에는 PR 본문이 아니라 개별 커밋 메시지 4개가 들어갔다(6.3) |
| 2026-11-25 | 병합 후 `push(main)` CI → [run 45584159610](https://github.com/gustn395/YHS_Live_Streaming_Platform/actions/runs/45584159610) | `success`, 모든 단계 성공. Artifact `test-reports`(12,339 B)·`backend-war`(19,903,666 B). 워크플로 `Backend CI`(id `429991382`) `active` |
| 2026-11-25 | `workflow_dispatch` 수동 실행 (사용자가 Actions 화면 `Run workflow`) | 사용자 화면 확인: Run 성공, `backend-build` 성공, Gradle 9.9.2 `clean build` 성공, Artifact `backend-war`(19 MB)·`test-reports`(12.1 KB). **Run ID·실행 브랜치는 미확인**(공개 API 요청 한도 초과로 조회 실패) |

### 6.1 GitHub 실행 검증 순서

각 Commit·Push와 GitHub 설정 변경은 사용자 승인 후 진행한다. **병합 전**에는 `pull_request` 트리거로만 CI를 검증한다. `push(main)`과 `workflow_dispatch`는 **최초 main 병합 후** 검증한다. `workflow_dispatch`는 기본 브랜치에 워크플로가 있어야 실행할 수 있기 때문이다(2장 트리거).

사전 단계: PR #4 `pull_request` CI 첫 실행 성공, CodeRabbit 첫 자동 리뷰(`COMMENTED`) 확인 (6장 기록).

| # | 시점 | 단계 | 상태 |
| --- | --- | --- | --- |
| 1 | 병합 전 | CodeRabbit 지적(`workflow_dispatch` 검증 시점) 수정 | 완료 |
| 2 | 병합 전 | 로컬 문서 검증(`git diff --check`) | 완료 |
| 3 | 병합 전 | 수정 Commit·Push | 완료 (`b067249`) |
| 4 | 병합 전 | 같은 PR #4에서 `backend-build` 재실행·성공 확인 | 완료 (run 45571903051) |
| 5 | 병합 전 | CodeRabbit 재리뷰, 지적 해소 확인 | 완료 (새 지적 0건) |
| 6 | 병합 전 | 사용자가 5.4 Ruleset 적용(필수 체크는 `backend-build`만) → Claude Code가 API로 규칙 확인 | 완료 (4.1) |
| 7 | 병합 전 | 의도적 실패 테스트 Commit·Push (Q5) | 완료 (`9e91d31`) |
| 8 | 병합 전 | CI 실패, 로그의 실패 테스트 이름, `test-reports` 업로드·`backend-war` 미업로드, 병합 차단(사용자 화면) 확인 | 완료 (기록 A, run 45581787360) |
| 9 | 병합 전 | 실패 테스트 되돌리기 Commit·Push | 완료 (`b7b6c54`) |
| 10 | 병합 전 | 같은 PR에서 CI 성공, 병합 차단 해제 확인 | 완료 (기록 B, run 45583570195) |
| 11 | 병합 | 사용자가 Squash Merge (이때 처음으로 main에 워크플로가 생긴다) | 완료 (`9c1166e`) |
| 12 | **최초 main 병합 후** | `push(main)` CI 실행·성공 확인 | 완료 (run 45584159610) |
| 13 | **최초 main 병합 후** | Actions 화면 `Run workflow`로 `workflow_dispatch` 수동 실행·성공 확인 (선행 조건: main에 `backend-ci.yml` 존재) | 완료 (사용자 화면 확인, Run ID 미확인) |

병합 후(12·13) 결과는 main에 직접 Push할 수 없으므로 후속 PR에서 이 문서에 기록했다.

### 6.2 Step 4 완료 기준

아래를 모두 실제로 확인해야 Step 4를 완료로 본다. 확인 증거(실행 링크, API 조회, 화면)가 없는 항목은 완료로 표시하지 않는다.

- 병합 전: PR #4에서 `pull_request` CI 성공(6.1의 4·10), CodeRabbit 자동 리뷰 생성과 지적 해소(5), 실패 테스트 시 CI 실패·병합 차단(8), 복구 시 성공·차단 해제(10)
- main 보호: Ruleset이 적용되고 필수 체크가 `backend-build` 하나다(6). CodeRabbit은 필수 조건이 아니다
- 병합: Squash Merge로 병합된다(11)
- 병합 후: `push(main)` CI 성공(12), `workflow_dispatch` 수동 실행 성공(13)
- 테스트 3개 개별 결과와 wrapper 검증 로그: Actions 로그나 `test-reports` Artifact로 확인한 경우에만 완료로 기록한다

판정 (2026-11-25): 병합 전·main 보호·병합·병합 후 기준을 실제로 확인해 **Step 4 구현과 주요 검증은 완료**로 본다. 마지막 기준(테스트 개별 결과, wrapper 검증 로그)은 일부만 확인했다. 테스트 건수는 기록 A의 로그(`4 tests completed, 1 failed`)로만 확인했고, 성공한 Run의 개별 결과와 wrapper 검증 로그는 6.3에 미확인으로 남긴다.

### 6.3 미확인 항목

추측으로 채우지 않는다. 확인하면 6장에 행을 추가한다.

| 항목 | 이유 | 확인 방법 |
| --- | --- | --- |
| 성공한 Run(45571903051, 45583570195, 45584159610)의 테스트 3개 개별 결과 | Actions 로그·Artifact 내용은 인증이 필요 | Run의 `test-reports` Artifact를 받아 압축을 푼 뒤 `app/build/reports/tests/test/index.html` (Artifact 루트가 `backend/`라 경로에 `backend/`가 붙지 않는다) |
| wrapper 검증(`validate-wrappers`) 로그 | 같음 | Run 로그의 `Set up Gradle` 단계 |
| `workflow_dispatch` Run ID·실행 브랜치 | 공개 API 요청 한도 초과로 조회 실패 | Actions → Backend CI → 이벤트 `workflow_dispatch` 필터 |
| 저장소 Merge 방식 체크박스, Squash 기본 메시지 | 비인증 API 응답에 필드 없음. `9c1166e`의 본문에 PR 본문이 아니라 개별 커밋 메시지 4개가 들어갔다. 설정 값 때문인지 병합 화면에서 메시지를 고친 것인지는 모른다 | `Settings → General → Pull Requests` |
| `Automatically delete head branches` 설정 값 | 원격 브랜치 삭제는 확인했지만 설정 화면은 보지 않았다 | `Settings → General → Pull Requests` |
| Actions 권한(5.1) 적용 여부 | 비인증 API로 볼 수 없음 | `Settings → Actions → General` |
| `require_extra_approval_for_unattributed_changes: true`의 의미 | 4.1 참고 | GitHub 문서 또는 Ruleset 화면 |
| 공개 API `mergeable_state`가 `unstable`이었던 이유 | 6장 기록 A 행 참고 | 인증된 API로 같은 조건 재현 시 확인 |

## 7. 미결·후속

- CodeRabbit 리뷰를 필수 승인·필수 체크로 둘지: 후속 단계에서 검토
- AGENTS.md·CLAUDE.md 작성(Spring Boot 3.5.x / Framework 6.2.x 호환 API 우선 원칙 포함): 별도 단계
- Action 버전 자동 갱신(Dependabot 등): 미정
- CD(AWS·OIDC 등): Streaming Phase 4 이후 별도 단계
- GitHub Actions Bot AI 리뷰(`github-actions[bot]` 명의 AI 리뷰)와 그 구현 방식인 Claude Code Review(`anthropics/claude-code-action`, `CLAUDE_CODE_OAUTH_TOKEN`): Step 4에서 제외. Agent Harness / Workflow 구축 단계에서 별도로 검토한다. 필요한 Actions 허용 목록 변경도 그때 정한다
