# frontend — PROGRESS

작업 상태(`FE-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

| ID | 작업 | Issue | 상태 |
| --- | --- | --- | --- |
| FE-W01 | FE-01 React 공통 인증 기반(FE-01-1~8: 작업 환경, 공통 구조, API Client, 인증 상태, 공통 오류 처리, 라우팅, 테스트·검증, 문서·리뷰) | #11 | 완료(#14) |
| FE-W02 | FE-02 Frontend CI 구축 및 Backend·Frontend CI 독립성 보장([fe02-ci-split.md](../testing/fe02-ci-split.md)) | #16 | 완료(#18) |
| FE-W03 | FE-03 회원가입 화면 및 CSRF 토큰 연동(member MB-W12의 가입 부분) | #20 | 완료(#23) |
| FE-W04 | FE-04 로그인·이메일 OTP 화면, Google 로그인 시작·오류 안내, 로컬 E2E(Playwright)(member MB-W12의 로그인 부분) | #26 | 진행 중(정책 FE-D034~FE-D042 기록, 화면·단위 테스트 구현, 자체 리뷰 반영, 로컬 E2E Full 3회 통과. M-03 미실행, 커밋 전) |

- 연결: member [MB-W12](../member/PROGRESS.md#1-작업-상태)(React 인증 화면).

## 2. 차단 요소

Backend 협의 안건이다. 결정 주체는 Backend member 담당이며 FE-04 범위의 진행을 막지 않는다([FE-D034](decisions.md#1-확정-결정)).

- 새로고침 뒤 인증 복원에 쓸 현재 회원 조회 API — member BE-07(#28)이 `GET /api/v1/members/me`를 제공한다(계약 원본: [member module.md](../member/module.md) 6장). 실제 Auth Adapter 연결은 후속 FE 작업 — [FE-U02](decisions.md#2-미결).
- 만료 401과 미인증 401의 구분, 로그아웃·Refresh 화면 연동 — [FE-U02](decisions.md#2-미결).

FE-W02의 GitHub Settings 작업이다. 사용자가 하며, 순서·절차는 [fe02-ci-split.md](../testing/fe02-ci-split.md) 5장이 원본이다.

- Actions 허용 목록에 Frontend CI가 쓰는 Action 추가.
- main Ruleset 필수 체크 전환(`backend-build` → Gate 2개)과 복구 기간의 일반 PR Merge 보류.
