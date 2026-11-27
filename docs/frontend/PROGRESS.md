# frontend — PROGRESS

작업 상태(`FE-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

| ID | 작업 | Issue | 상태 |
| --- | --- | --- | --- |
| FE-W01 | FE-01 React 공통 인증 기반(FE-01-1~8: 작업 환경, 공통 구조, API Client, 인증 상태, 공통 오류 처리, 라우팅, 테스트·검증, 문서·리뷰) | #11 | 완료(#14) |
| FE-W02 | FE-02 Frontend CI 구축 및 Backend·Frontend CI 독립성 보장([fe02-ci-split.md](../testing/fe02-ci-split.md)) | #16 | 완료(#18) |
| FE-W03 | FE-03 회원가입 화면 및 CSRF 토큰 연동(member MB-W12의 가입 부분) | #20 | 구현·자동 테스트·S6·S10 리뷰 반영·M-02(8번 제외) 완료(커밋 전), S7 전 |

- 연결: member [MB-W12](../member/PROGRESS.md#1-작업-상태)(React 인증 화면).

## 2. 차단 요소

Backend 협의 안건이다. 결정 주체는 Backend member 담당이며 FE-01 범위의 진행을 막지 않는다.

- 인증 쿠키 이름·속성(HttpOnly·SameSite·Path) — [FE-U02](decisions.md#2-미결).
- 현재 회원 조회 API 유무와 응답 형식 — [FE-U02](decisions.md#2-미결).
- 1차 인증 뒤 OTP 대기 상태 표현 — [FE-U08](decisions.md#2-미결).
- 만료 401과 미인증 401의 구분 code, Refresh 엔드포인트, 재요청 정책 — [FE-U02](decisions.md#2-미결).
- Google OIDC 콜백 경로와 흐름 — [FE-U02](decisions.md#2-미결).

FE-W02의 GitHub Settings 작업이다. 사용자가 하며, 순서·절차는 [fe02-ci-split.md](../testing/fe02-ci-split.md) 5장이 원본이다.

- Actions 허용 목록에 Frontend CI가 쓰는 Action 추가.
- main Ruleset 필수 체크 전환(`backend-build` → Gate 2개)과 복구 기간의 일반 PR Merge 보류.
