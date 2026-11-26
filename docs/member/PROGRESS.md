# member — PROGRESS

작업 상태(`MB-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

| ID | 작업 | Issue | 상태 |
| --- | --- | --- | --- |
| MB-W01 | DB 기반·스키마(JPA·PostgreSQL·Flyway·Testcontainers) | #10 | 진행 중 |
| MB-W02 | Member Entity·포트·JPA 어댑터 | #10 | 진행 중 |
| MB-W03 | 비밀번호 해시·정책·유출 비밀번호 검사(HIBP) | #10 | 진행 중 |
| MB-W04 | 회원가입 API | #10 | 진행 중 |
| MB-W05~W08 | JWT·쿠키, 로그인 1차 인증, OTP, 토큰 발급(BE-03) | — | 대기 |
| MB-W09~W10 | Refresh Rotation, 로그아웃(BE-04) | — | 대기 |
| MB-W11 | Google OIDC(BE-05) | — | 대기 |
| MB-W12 | React 인증 화면(FE 번호 체계) | — | 대기 |
| MB-W13 | Swagger Spec 대조(각 작업과 병행) | — | 진행 중 |

## 2. 차단 요소

- ~~[G-U01](../policy.md#4-공통-미결) DB 연동 테스트 방식 미결~~ — 해소([G-D009](../policy.md#3-공통-결정-이력), 2026-11-26).
- ~~global 공통 기반(Issue #8)~~ — 완료(PR #9, 2026-11-26).
- BE-03(MB-W05)에서 할 일로 넘겨받음: OpenAPI cookie 방식 보안 스킴 정의(인증 쿠키 이름·속성 확정 뒤). 근거: [docs/global/module.md](../global/module.md) 1장.
- [G-U02](../policy.md#4-공통-미결) CSRF 방식 미결 — MB-W05 전에 결정해야 한다.
