# Step 4: GitHub Actions CI + CodeRabbit + main 보호

- 브랜치: `feature/infra-github-actions-ci`
- 작성일: 2026-11-25
- 상태: **로컬 구현·검증 완료. GitHub 실제 실행, CodeRabbit 연동, Ruleset 적용은 미실행** (5장 체크리스트, 6장 기록)
- 범위: CI만. AWS·ECR·EC2·Docker Compose 배포·GitHub OIDC·CD Workflow는 제외(Streaming Phase 4 실기능 구현과 최초 수동 AWS 배포 검증 이후 별도 단계)

## 1. 결정 사항

2026-11-25 사용자 결정 (정책 인터뷰 Q1~Q6).

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
| 트리거 | `pull_request`(→ main), `push`(main), `workflow_dispatch`. **paths 필터 없음** |
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

### 6.1 GitHub 실행 검증 계획 (미실행)

각 Commit·Push는 사용자 승인 후 진행한다.

1. Step 4 커밋 Push → PR 생성(사용자) → `backend-build` 성공, Artifact `test-reports`·`backend-war` 확인, wrapper 검증 로그 확인, CodeRabbit 자동 리뷰 확인
2. 사용자가 5.4 Ruleset 적용 → Claude Code가 API로 규칙 확인
3. 실패 테스트 커밋 Push → CI 실패, 로그에 실패 테스트 이름, `test-reports` 업로드, `backend-war` 미업로드 확인
4. PR 병합 버튼 차단 확인(사용자 화면)
5. 되돌리기 커밋 Push → 같은 PR에서 CI 성공 → 차단 해제 확인
6. `workflow_dispatch` 1회 실행
7. 사용자가 Squash Merge → main `push` CI 성공 확인

## 7. 미결·후속

- CodeRabbit 리뷰를 필수 승인·필수 체크로 둘지: 후속 단계에서 검토
- AGENTS.md·CLAUDE.md 작성(Spring Boot 3.5.x / Framework 6.2.x 호환 API 우선 원칙 포함): 별도 단계
- Action 버전 자동 갱신(Dependabot 등): 미정
- CD(AWS·OIDC 등): Streaming Phase 4 이후 별도 단계
