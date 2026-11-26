# member — PROGRESS

작업 상태(`MB-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

- 미정 (결정 전)

## 2. 차단 요소

- [MB-U01](decisions.md#2-미결) 인증 방식 미결 — Member 인증 구현 전에 결정해야 한다.
- ~~[G-U01](../policy.md#4-공통-미결) DB 연동 테스트 방식 미결~~ — 해소([G-D009](../policy.md#3-공통-결정-이력), 2026-11-26).
- global 공통 기반(Issue #8, [G-D001](../policy.md#3-공통-결정-이력)) — Member 구현(BE-02) 전에 완료해야 한다.
- BE-03(MB-W05)에서 할 일로 넘겨받음: OpenAPI cookie 방식 보안 스킴 정의(인증 쿠키 이름·속성 확정 뒤). 근거: [docs/global/module.md](../global/module.md) 1장.
