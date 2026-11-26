# global — PROGRESS

작업 상태(`GL-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

| ID | 작업 | Issue | 상태 |
| --- | --- | --- | --- |
| GL-W01 | global 모듈·문서 | #8 | 완료(PR #9) |
| GL-W02 | 공통 예외·오류 응답 | #8 | 완료(PR #9) |
| GL-W03 | Spring Security 기본 구성 | #8 | 완료(PR #9) |
| GL-W04 | 공통 인증 계약 | #8 | 완료(PR #9) |
| GL-W05 | springdoc·OpenAPI 공통 | #8 | 완료(PR #9). 보안 스킴은 MB-W05로 이관 |
| GL-W06 | JPA 공통 설정(감사 기능, 생성·수정 시각 기반 클래스) | #10 | 진행 중(G-D010) |

## 2. 차단 요소

- [G-U02](../policy.md#4-공통-미결) CSRF 방식 미결 — 쿠키 인증을 넣는 MB-W05 전에 결정해야 한다.
