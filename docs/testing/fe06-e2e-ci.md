# FE-06: Playwright E2E GitHub Actions CI 연동

- Issue: #32 · 브랜치: `feature/32-frontend-e2e-ci` · 기준 커밋: `origin/main` `b8b789c`
- 작성일: 2026-11-28
- 상태: **구현·L1·L2·로컬 E2E 회귀·S6 반영 완료. L3·L4 전. 필수 체크 전환과 이월 검증은 FE-U12**
- 범위: GitHub Actions에서 PostgreSQL·Redis·Mailpit·Backend·Frontend를 띄워 Playwright E2E를 실행하는 Workflow와 Gate,
  공용 변경 감지·Gate의 E2E 영역 추가, 검증과 Ruleset 필수 체크 전환 조건. E2E 실행 범위·태그·HIBP 스텁 사용·결과물은
  [frontend decisions.md](../frontend/decisions.md) FE-D044가 원본이다. 기존 Backend·Frontend CI 공통 결정은
  [fe02-ci-split.md](fe02-ci-split.md) 1장이 원본이고, 이 문서는 E2E 영역을 더한 부분만 적는다.

## 1. 결정 사항

2026-11-27~28 사용자 결정(FE-06 정책 1~3라운드, Q15는 S6 리뷰 처리 때). 이 문서의 ID는 이 문서 안에서 매긴 번호이고, "출처"는 정책 라운드다.

| ID | 결정 | 출처 |
| --- | --- | --- |
| Q1 | 새 Workflow `.github/workflows/e2e-ci.yml`을 둔다. 트리거는 main 대상 `pull_request`, main `push`, `workflow_dispatch`다. `pull_request_target`은 쓰지 않는다. Job은 변경 감지 → 조건부 E2E → 최종 Gate `e2e-ci`다. 기존 `backend-ci`·`frontend-ci` Workflow의 동시 실행 설정은 바꾸지 않는다 | 1라운드 Q1·Q2, 3라운드 Q6 |
| Q2 | 변경 감지: 기존 `detect_changes.py`·`detect-changes` Action에 `e2e` 출력을 더한다. Backend 변경은 Backend·E2E, Frontend 변경(`frontend/e2e/**` 포함)은 Frontend·E2E, E2E 전용 Workflow·Compose 변경은 E2E만, 공용 감지·Gate 코드 변경은 관련 CI 모두를 실행한다. 미분류 경로는 기존의 안전한 판정(모두 실행), docs-only는 기존 생략, 감지 실패는 FALLBACK이다. 승인된 E2E 전용 경로 분류 외에는 기존 Backend·Frontend 판정을 바꾸지 않는다. 기존 테스트를 유지하고 e2e 분류·출력 회귀 테스트를 더한다 | 1라운드 Q1, 3라운드 Q1 |
| Q3 | Gate: 기존 `gate.py`·`ci-gate` Action에 `area=e2e`를 더한다. NORMAL·SKIPPED·FALLBACK·FULL·FAILED의 의미는 fe02 Q12 그대로다. 예상하지 못한 Job 생략, 취소, 인프라 기동 실패, 테스트 미실행을 성공으로 처리하지 않는다. 기존 Backend·Frontend Gate의 회귀 테스트를 함께 한다 | 1라운드 Q2, 3라운드 Q2 |
| Q4 | 기동: PostgreSQL·Redis·Mailpit은 기존 `infrastructure/docker/e2e/compose.yml`을 재사용한다. Backend는 `./gradlew :app:bootWar`로 만든 실행 가능한 WAR(`*-plain.war` 제외, 정확히 1개여야 하고 0개·2개 이상이면 실패)를 `java -jar`로 백그라운드 실행하고 기동을 확인할 때까지 기다린다. Backend 빌드 설정과 코드는 바꾸지 않는다(정정: 처음 결정의 `:app:bootJar`는 이 저장소의 WAR 패키징과 맞지 않아 바로잡았다, 7장). Frontend는 `npm run build`(checkDist 포함) 뒤 `vite preview`로 실행한다: 포트 4173, loopback 바인드, 포트가 겹쳐도 다른 포트로 바꾸지 않음, `E2E_BASE_URL`과 `MEMBER_OAUTH_PUBLIC_BASE_URL`의 출처 일치, `FRONTEND_API_PROXY_TARGET`은 E2E 전용 Backend, preview에서 API 프록시가 실제로 동작하는지 확인, 기동 시간 제한과 종료 처리. 운영 Frontend 제공 방식(FE-U01)은 정하지 않는다 | 1라운드 Q3·Q4, 2라운드 Q3, ④ 계획 Q1(정정) |
| Q5 | HIBP 스텁(CI 쪽 조건): Backend HIBP Client의 실제 계약을 지키고 `GET /range/{prefix}`만 처리하며 prefix 형식을 검증한다. 예상하지 못한 요청은 실패로 처리한다. loopback 바인드, 새 의존성·이미지 없음, 기존 `MEMBER_PASSWORD_HIBP_BASE_URL` 사용, 실제 HIBP로 자동 대체 호출 금지, Backend가 스텁을 실제로 호출했다는 증거 확인, Health Check·시간 제한·Cleanup을 둔다 | 1라운드 Q6, 2라운드 Q4 |
| Q6 | 비밀·권한: 키와 비밀번호는 실행마다 무작위로 만들고 곧바로 `::add-mask::`로 등록한 뒤 `$GITHUB_ENV`로 다음 Step에 넘긴다. Backend의 실제 키 형식과 환경변수 계약을 따른다. 운영 비밀과 Repository Secret을 쓰지 않는다. 필요한 Google OAuth 설정은 일회성 더미 값이다. 비밀 값 출력, 환경변수 전체 출력, `set -x`를 쓰지 않는다. Workflow 권한은 최소로 두고 checkout은 인증 정보를 남기지 않는다(`persist-credentials: false`) | 3라운드 Q6 |
| Q7 | 동시 실행: PR은 PR 번호 그룹으로 같은 PR의 이전 실행만 취소한다. main push와 `workflow_dispatch`는 실행마다 고유한 Run ID 그룹을 써서 취소되거나 대기 중에 다른 실행으로 대체되지 않게 한다(`cancel-in-progress: false`만으로는 같은 그룹의 대기 실행이 보존되지 않는다) | 3라운드 Q3 |
| Q8 | 시간 제한: 변경 감지 Job 5분, E2E Job 25분, Gate 5분. Step은 compose 기동 3분, Backend 기동 대기 3분, preview·스텁 기동 대기 각 1분, PR E2E 5분, 전체 E2E 10분, Cleanup 3분이다. `bootWar`(정정, Q4)·`npm ci`·Frontend build·Chromium 설치를 포함한 Job 전체 시간 예산을 구현 계획에서 확인하고 Cleanup 시간을 확보한다. Job 시간 초과나 강제 취소 때는 Cleanup이 보장되지 않는다 | 3라운드 Q4 |
| Q9 | 재시도: Playwright `retries`는 0이고 Step 자동 재시도는 없다. 수동 Re-run을 하면 처음 실패의 원인과 재실행 결과를 함께 6장에 적고, 원인을 제품 결함·테스트 결함·환경 불안정·원인 미확인 중 하나로 분류한다. 자동 재시도나 임의의 시간 제한 증가로 간헐 실패를 숨기지 않는다 | 3라운드 Q5 |
| Q10 | 실패 로그: Artifact는 올리지 않는다. 실패 때만 Backend 로그 최대 200줄과 컨테이너 로그 최대 50줄을 민감 정보를 거른 뒤 출력한다. 줄 수 제한만으로 보호가 끝났다고 보지 않으며, 안전하게 거를 수 없는 로그는 출력하지 않는다. Playwright 결과는 assertion·오류 메시지에 민감 정보가 없는지 확인한다. Docker 상태·실패 단계·종료 코드·실행 시간은 출력해도 된다. Mailpit 메일 본문, OTP, 비밀번호, 쿠키, 토큰, 요청·응답 원문, 환경변수 덤프는 출력하지 않는다. Step Summary에는 판정, 실행 범위, 소요 시간, 실패 단계, Cleanup 결과 등 안전한 요약(개수·고정 토큰)만 남긴다. 로그 필터·요약 기능을 구현하면 그 기능도 검증한다 | 1라운드 Q7, 3라운드 Q7(문구는 승인 원문 "등 안전한 요약"으로 정정, S6 R10) |
| Q11 | Cleanup: 성공·실패와 관계없이 Backend·Frontend·HIBP 스텁 프로세스 종료, E2E 전용 compose `down -v`, 임시 파일 정리를 시도하고 결과를 확인·기록한다. 일회용 러너에서 Cleanup 실패만으로 성공한 E2E를 실패로 바꾸지 않고 warning과 Step Summary에 남긴다. 환경 준비 실패, Backend 기동 실패, Playwright 실행 실패, 테스트 미실행은 Gate 실패다. 취소·시간 초과로 정리 결과를 확인할 수 없으면 성공으로 기록하지 않는다 | 3라운드 Q8 |
| Q12 (L4 범위 부분 변경 → Q15) | 검증(fe02 Q8 구조): L1 감지·Gate unittest와 기존 Backend·Frontend 회귀, L2 임시 Git 저장소로 실제 경로 분류, L3 FE-06 구현 PR의 NORMAL과 E2E-01·02, L4 main push FULL, 수동 실행 FULL, docs-only SKIPPED, 임시 감지 실패 FALLBACK, 임시 테스트 실패 FAILED. E2E 전용 경로만 바꿨을 때 Backend·Frontend build Job이 실제로 생략되는지도 확인한다. 실패 재현용 검증 PR은 main에 Merge하지 않는다. 원격 브랜치 삭제와 GitHub Settings 변경은 사용자가 한다 | 3라운드 Q9 |
| Q13 (완료 기준 부분 변경 → Q15) | FE-06 완료 기준: Workflow·관련 문서 Merge, L1~L4 검증, 성공·실패 실행의 Cleanup 기록 확인, L4 실제 결과의 문서 반영. 충족하면 FE-U11을 해결 처리하고 FE-W06을 완료로 바꾼다. FE-06 완료 뒤 FE-05를 진행한다 | 3라운드 Q10 |
| Q14 (이월 검증 추가 → Q15) | 필수 체크 전환(FE-U12): Gate `e2e-ci`는 처음에 필수 체크가 아니다. 전환 조건은 실제 E2E가 실행된 PR 5건 이상 안정 통과(SKIPPED는 세지 않음), main push 전체 E2E 성공, 수동 전체 E2E 성공, NORMAL·SKIPPED·FALLBACK·FULL·FAILED 판정 검증, 환경 때문인 간헐 실패와 미해결 인프라 문제 없음, 성공·실패 때의 Cleanup 확인, Ruleset 전환·복구 절차 확보다. Cleanup warning이 난 실행은 안정 통과로 세지 않는다. 제품 결함을 정상적으로 찾아낸 실패와 CI 환경 불안정 때문인 실패를 구분한다. Ruleset 변경은 사용자가 한다. FE-06 구축 완료와 전환 완료는 따로 관리한다 | 2라운드 Q1, 3라운드 Q8·Q10 |
| Q15 | 원격 검증 간소화와 완료 기준(Q12 L4 범위·Q13 완료 기준을 바꾸고 Q14에 이월 항목을 더한다): (1) 유지: L1·L2, 로컬 E2E 회귀, Mutation·보안 검증, L3 구현 PR의 `backend-ci`·`frontend-ci` PASS와 `e2e-ci` NORMAL(E2E-01·02 성공), CodeRabbit 최종 리뷰, Squash Merge, L4 Merge 뒤 자동 실행되는 main push의 `e2e-ci` FULL(E2E-01~03 성공), 실제 CI 실행의 HIBP 스텁 호출 증거와 Cleanup 결과 확인. (2) 생략: docs-only SKIPPED·E2E 전용 경로 분류·FALLBACK·FAILED 검증 PR, `workflow_dispatch` 수동 FULL, 최종 검증 기록만을 위한 문서 PR. 이를 위한 임시 Branch·Commit·Push·PR은 만들지 않는다. (3) 생략한 검증은 FE-U12(필수 체크 전환) 전까지 이월하고, 후속 개발에서 그 시나리오가 자연스럽게 생기면 실제 실행 결과를 쓴다. 이미 확인한 검증은 FE-U12에서 반복하지 않는다. S6 리뷰에서 실제 실행으로만 최종 확인된다고 한 항목(기동 대기 시간, 실패 단계 기록, 볼륨 정리 확인)도 로컬 계산·모의 검증으로 확인하고, GitHub 실패 상황에서만 확인할 수 있는 부분은 FE-U12로 이월한다. 확인하지 않은 항목은 PASS로 적지 않는다. (4) 완료 기준: 구현·관련 문서, L1·L2 통과, 로컬 E2E 회귀·보안 검증, 내부 코드 리뷰·CodeRabbit 리뷰, 구현 PR의 BE·FE CI PASS와 E2E CI NORMAL·E2E-01·02 성공, Squash Merge, main push의 E2E CI FULL·E2E-01~03 성공, 실제 실행의 스텁 호출 증거와 Cleanup 결과 확인. 모두 채우면 FE-06을 완료하고 FE-U11을 해결 처리한다. FE-U12는 FE-06 완료와 FE-05 개발을 막지 않는다. (5) Merge 뒤 실제 CI 결과는 Issue #32 댓글과 작업 상태 기록에 남기고, 이 문서 6장에는 이후 관련 문서를 바꾸는 작업에서 함께 반영한다(기록만을 위한 PR은 만들지 않는다) | S6 Q1 보완, 2026-11-28(이유: FE-02에서 사용자가 검증 Branch·PR을 반복 생성·확인하는 데 시간이 많이 들었다) |

## 2. CI 구성

구현: `.github/workflows/e2e-ci.yml`. 공용 감지·Gate(`.github/ci/detect_changes.py`·`gate.py`, `.github/actions/*`)는 fe02 2장
구성에 `e2e` 출력·영역을 더했다. Backend·Frontend Workflow는 감지 출력 `e2e`를 Gate에 넘기는 줄만 더했다(각 2줄).

| 항목 | 내용 |
| --- | --- |
| 트리거·권한 | `pull_request`(→ main), `push`(main), `workflow_dispatch`. paths 필터 없음. 권한 `contents: read`만, secret 없음 |
| Job | `e2e-changes`(5분) → `e2e-run`(25분, `e2e == 'run'`일 때) → `e2e-ci`(Gate, 5분, `if: always()`) |
| 동시 실행 | PR: 그룹 `e2e-ci-pr-<PR 번호>`, 이전 실행 취소. main push·수동: 그룹 `e2e-ci-run-<run_id>`, 취소·대체 없음 |
| 서버 | compose(`yhs-e2e`, 비밀번호는 실행마다 생성) → `:app:bootWar` → 실행 WAR 1개 선택 → HIBP 스텁(`/healthz`) → Backend(`/actuator/health`) → `npm run build` → `vite preview`(`localhost:4173`, `--strictPort`, `/`·`/api/v1/auth/csrf` 확인) → 4173·18099 loopback 확인 |
| 실행 범위 | 실행 전 `playwright test --list --reporter=json`으로 필수 집합을 만든다. PR은 `--grep-invert @slow`, 그 밖은 전체. 결과 JSON은 `PLAYWRIGHT_JSON_OUTPUT_FILE`로 list 출력과 분리한다 |
| 결과 검증 | `e2e_report.py`(0건·skipped·flaky·unexpected·필수 누락·범위 밖 실행·PR의 @slow·FULL의 @slow 미실행 → 실패, 실행 수를 출력), `hibp_stub_check.py`(정상 호출 ≥ 실행 수, 예상 밖 요청 0) |
| 로그 | `log_filter.py`(Allowlist): Playwright 결과·집계 줄만, 실패 때 Backend 최대 200줄·postgres·redis 최대 50줄. Mailpit 로그·환경변수는 출력하지 않는다. Artifact 없음 |
| Cleanup | `if: always()`, 3분, 항상 exit 0. 프로세스(preview·Backend·스텁) 종료 → `down` 전에 이 프로젝트 컨테이너의 Mounts에서 Docker 볼륨 이름을 모음(bind mount 제외) → `compose down -v` → 컨테이너 0개, 모은 볼륨이 남지 않음, 프로젝트 라벨 볼륨 0개, 4173·18080·18099 닫힘 → 작업 폴더·Playwright 결과 폴더 삭제. Docker 조회 실패는 성공으로 보지 않는다. 실패 항목은 warning과 Step Summary |
| 실패 진단 | `if: failure()`. `failed_step.py`가 Step `id`·outcome(고정 토큰)으로 첫 실패 단계를 Step Summary에 적는다(찾지 못하면 "확인 불가"). compose 상태·로그는 비밀 생성 전 실패에서도 보이도록 `ps`·`logs`·`down`에만 임시 비밀번호 값을 준다(compose up·Backend·실제 DB 인증에는 쓰지 않는다) |

- Step 시간 제한: Q8에 정한 Step 외에는 준비·비밀·WAR 선택·loopback·목록·결과 검증·스텁 검증 각 1분, JDK·Gradle·Node·`npm ci`·build
  각 3분, Chromium 5분, bootWar 6분, 진단 2분이다. 합이 Job 25분을 넘지만 Job 제한이 우선한다. 예상 소요는 10~14분이고 실측은
  L3 결과로 남긴다(Q15 (5)).
- 기동 대기는 경과 시간 마감(`SECONDS`)으로 끝낸다. 마지막 반복(curl 제한·sleep)까지 더한 최대 대기가 Step 제한보다 짧아 자체
  오류(`::error`)를 남길 여유가 있다: 스텁·preview 마감 40초(최대 약 43·45초, 제한 60초), Backend 마감 150초(최대 약 154초,
  제한 180초), compose `--wait-timeout` 140초(제한 180초).
- **제한**: Job 시간 초과나 강제 취소 때는 Cleanup이 실행되지 않을 수 있다. 그 실행은 정리 결과를 확인하지 못했으므로 성공으로
  기록하지 않는다(Q11). GitHub 호스트 러너는 Job이 끝나면 버려진다.
- Action은 기존 Workflow와 같은 SHA를 쓴다(버전 기록은 [fe02 2.3](fe02-ci-split.md#23-action-버전)). 새 Action이 없어 Actions 허용
  목록은 바꾸지 않는다.
- HIBP 스텁 계약(요청·응답·통계)의 원본은 [frontend test-spec](../frontend/test-spec.md) FE-T040이다.

### 2.1 감지 출력과 허용 조합

fe02 2.1 표에 `e2e` 열을 더한다. 규칙: backend·frontend 중 하나라도 run이면 e2e도 run.

| mode | reason | backend / frontend / e2e | 언제 |
| --- | --- | --- | --- |
| NORMAL | `changed` | 하나 이상 run(e2e 규칙 지킴) | PR 변경이 분류됨. E2E 전용 경로만 바뀌면 skip / skip / run |
| NORMAL | `docs_only` | skip / skip / skip | PR 변경이 모두 "생략" |
| FALLBACK | `git_error`·`empty_diff`·`invalid_input`·`unknown_event` | run / run / run | fe02 2.1과 같음 |
| FULL | `push_main` / `dispatch` | run / run / run | main push / 수동 실행 |

Gate 판정표는 fe02 2.2 그대로이고 대상만 `area`(backend·frontend·e2e)로 고른다. 세 Gate 모두 세 대상의 조합을 먼저 확인하므로
`e2e` 출력이 빠지거나 허용 밖 값이면 FAILED다. `e2e-run` Job이 failure·cancelled·예상 밖 skipped이면 FAILED다(기동 실패·테스트
미실행·결과 검증 실패는 Job failure다).

## 3. 변경 감지 규칙의 우선순위·예외

fe02 3장 표에 아래 두 규칙을 더한다. 위에서부터 처음 맞는 규칙을 쓴다.

| 위치 | 경로 | 분류 |
| --- | --- | --- |
| fe02 순위 2 다음 | `.github/workflows/e2e-ci.yml` | E2E |
| fe02 순위 3 다음 | `infrastructure/docker/e2e/**` | E2E |

- 판정: backend는 Backend·양쪽 분류, frontend는 Frontend·양쪽 분류가 있으면 run. e2e는 backend·frontend 중 하나가 run이거나
  E2E 분류가 있으면 run. 모두 skip이면 `docs_only`.
- `frontend/e2e/**`·`frontend/playwright.config.ts`는 지금처럼 Frontend다(Frontend CI의 lint·typecheck 대상). 그래서 Frontend와
  E2E가 함께 돈다. 그 밖의 `.github/**`(감지·Gate·결과 검증·로그 필터 스크립트)와 미분류 경로는 세 영역 모두 실행한다.
- `infrastructure/docker/e2e`와 같은 이름의 파일, `infrastructure/docker/e2ex/**` 같은 이웃 경로는 미분류(모두 실행)다.

## 4. 검증 계획 (L1~L4)

범위는 Q15다(Q12의 L4 범위를 줄였다).

| 단계 | 내용 | 기대 |
| --- | --- | --- |
| L1 | `python -B -I .github/ci/run_tests.py`(감지·Gate·결과 검증·로그 필터·스텁 검증·실패 단계), Frontend lint·typecheck·Vitest(스텁 FE-T040 포함)·build, `playwright test --list`, Workflow·action YAML 스키마 검증, run 블록 `bash -n` | 모두 통과, 기존 BE·FE 판정 불변 |
| L2 | unittest의 임시 Git 저장소: E2E 전용 경로, `frontend/e2e`, docs-only | E2E만 / FE·E2E / 생략 |
| 로컬 회귀 | test-spec 3장 Full(실제 HIBP) 1회, CI 방식(bootWar·스텁·preview) Full·PR 범위, 단언 3곳 실패 출력에 값이 없는지 | 통과 |
| 로컬 모의(S6) | 기동 대기 루프의 최대 소요(거부·무응답 서버), 실패 단계 계산(unittest), Cleanup 볼륨 확인(로컬 Docker: 정상 `down -v`와 볼륨을 남긴 경우) | 제한 안 자체 오류 / 첫 실패 단계 / 남은 볼륨을 잡음 |
| L3 | FE-06 구현 PR: `backend-ci`·`frontend-ci` PASS(회귀), `e2e-ci` NORMAL(E2E-01·02), 결과 검증·스텁 호출·loopback·Cleanup ok, 단계별 시간 | PASS |
| L4 | Merge 뒤 자동 실행되는 main push | `e2e-ci` FULL(E2E-01~03), 스텁 호출·Cleanup ok |
| 이월(FE-U12) | docs-only SKIPPED, E2E 전용 경로 변경 때 BE·FE build Job 생략, FALLBACK, FAILED(공개 출력에 OTP·쿠키·토큰 없음, 실패 단계 기록), `workflow_dispatch` FULL | 후속 개발에서 생긴 실제 실행으로 확인. 이를 위한 검증 Branch·PR은 만들지 않는다 |

## 5. 필수 체크 전환·복구 절차

FE-U12다. Q14의 전환 조건을 모두 채운 뒤 사용자가 웹 UI에서 한다. fe02 5장 방식을 따른다.

| 순서 | 작업 | 확인 |
| --- | --- | --- |
| E1 | 전환 조건 확인: 6장·Issue 기록에서 실제 E2E 실행 PR 5건 이상 안정 통과(SKIPPED·Cleanup warning 실행 제외), main push·수동 FULL 성공, 5가지 판정 검증, Q15 이월 항목, 미해결 환경 문제 없음. 이미 확인된 것은 다시 하지 않는다 | 6장·Issue |
| E2 | 사용자: Rulesets → `main-protection` → Export(JSON, 저장소 밖 보관) | 백업 파일 |
| E3 | 사용자: 필수 체크에 `e2e-ci`(소스 GitHub Actions) 추가. `backend-ci`·`frontend-ci`와 나머지 규칙은 그대로 | — |
| E4 | 공개 API로 필수 체크 3개 확인, 다음 PR에서 의도적 실패 → 차단 → 되돌리기 → 해제 확인 | 6장 |

- 복구: 필수 체크에서 `e2e-ci`만 빼고(Import 또는 수동) API로 확인한다. 사유는 Gate 오판정, 체크가 "Expected — Waiting"으로 멈춤,
  환경 문제로 모든 PR 차단, 사용자 판단이다. Bypass 추가·Ruleset 비활성화·Force Push는 하지 않는다. 모든 단계는 6장에 적는다.

## 6. 실행 기록

| 날짜 | 단계 | 결과 |
| --- | --- | --- |
| 2026-11-28 | L1·L2(로컬, 구현 브랜치 작업 트리) | 통과. 결과는 [frontend regression-log](../frontend/regression-log.md) FE-06 절 |
| 2026-11-28 | 로컬 회귀(로컬 절차 Full, CI 방식 Full·PR 범위, 단언 3곳 출력) | 통과. 결과는 같은 절 |
| 2026-11-28 | S6 반영 로컬 모의(대기 시간, 실패 단계, 볼륨 확인) | 통과. 익명 볼륨 2개에 프로젝트 라벨이 없음을 확인(라벨 검사만으로는 잔존을 못 잡음). 결과는 같은 절 |

- L3·L4는 아직 실행하지 않았다. 실행 결과는 Issue #32 댓글과 작업 상태 기록에 먼저 남긴다(Q15 (5)). Q15 이월 항목과 필수 체크
  전환은 FE-U12에서 확인한다.

## 7. 변경 이력

| 날짜 | 변경 |
| --- | --- |
| 2026-11-28 | 1장 Q1~Q14 기록(FE-06 정책 1~3라운드) |
| 2026-11-28 | Q4 정정: `:app:bootJar` → `:app:bootWar`(실행 가능한 WAR, plain 제외·정확히 1개). `backend/app`은 `war` 플러그인을 쓰고 `jar` 태스크를 끈다(④ 계획 Q1=a) |
| 2026-11-28 | 2~6장 작성(구현) |
| 2026-11-28 | S6 리뷰 반영: Q8 본문의 `bootJar`를 Q4 정정에 맞춤, Q10 문구를 승인 원문("등 안전한 요약")에 맞춤, 2장 Step 시간·Action SHA 중복 제거, 기동 대기·실패 진단·Cleanup 볼륨 확인 설명 추가 |
| 2026-11-28 | Q15 추가(원격 검증 간소화·완료 기준 변경), Q12·Q13·Q14 부분 변경 표시, 4장 검증 표를 Q15 기준으로 바꿈 |
