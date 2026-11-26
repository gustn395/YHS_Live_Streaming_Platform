# member — PROGRESS

작업 상태(`MB-W##`)와 차단 요소만 적는다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 작업 상태

| ID | 작업 | Issue | 상태 |
| --- | --- | --- | --- |
| MB-W01 | DB 기반·스키마(JPA·PostgreSQL·Flyway·Testcontainers) | #10 | 완료(PR #12) |
| MB-W02 | Member Entity·포트·JPA 어댑터 | #10 | 완료(PR #12) |
| MB-W03 | 비밀번호 해시·정책·유출 비밀번호 검사(HIBP) | #10 | 완료(PR #12) |
| MB-W04 | 회원가입 API | #10 | 완료(PR #12) |
| MB-W05~W08 | JWT·쿠키·CSRF·Redis 기반, 로그인 1차 인증, OTP, 토큰 발급(BE-03). 가입 IP Rate Limit(MB-D018)·로그인 재해시 포함 | #13 | 진행 중(구현·테스트·리뷰 완료, 커밋 전) |
| MB-W09~W10 | Refresh Rotation, 로그아웃(BE-04) | — | 대기 |
| MB-W11 | Google OIDC(BE-05) | — | 대기 |
| MB-W12 | React 인증 화면(FE 번호 체계) | — | 대기 |
| MB-W13 | Swagger Spec 대조(각 작업과 병행) | — | 진행 중 |

## 2. 차단 요소

- ~~[G-U01](../policy.md#4-공통-미결) DB 연동 테스트 방식 미결~~ — 해소([G-D009](../policy.md#3-공통-결정-이력), 2026-11-26).
- ~~global 공통 기반(Issue #8)~~ — 완료(PR #9, 2026-11-26).
- BE-03(MB-W05)에서 할 일로 넘겨받음: OpenAPI cookie 방식 보안 스킴 정의. 쿠키 이름·속성은 확정([MB-D013](decisions.md#1-확정-결정)). 근거: [docs/global/module.md](../global/module.md) 1장.
- ~~[G-U02](../policy.md#4-공통-미결) CSRF 방식 미결~~ — 해소([G-D014](../policy.md#3-공통-결정-이력), 2026-11-26).
- Frontend 협의 안건(Issue #13에서 FE 코드는 고치지 않음): FE-D014·FE-U04(XSRF 헤더 사용과 `GET /api/v1/auth/csrf` 호출, G-D014), FE-U02(쿠키 이름·오류 코드, MB-D011~D013), FE-U08(`status=OTP_REQUIRED`, MB-D015).
- 외부 공개 전 결정: [MB-U06](decisions.md#2-미결) 신뢰 프록시·클라이언트 IP, [MB-U04](decisions.md#2-미결) 키 교체·복구.
