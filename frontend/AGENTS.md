# frontend AGENTS

frontend(React SPA) 전용 규칙만 둔다. 공통 규칙은 [루트 AGENTS.md](../AGENTS.md)를 따르고 여기서 반복하지 않는다.

## 1. 역할과 책임

- 사용자 화면을 제공하는 React SPA. 관리자 화면(JSP)은 범위가 아니다([FE-D002](../docs/frontend/decisions.md#1-확정-결정)).

## 2. 코드 작업 범위

- 이 폴더(`frontend/`). Backend 코드·인증 정책·API 계약·CSRF 설정은 frontend 작업에서 바꾸지 않고 Backend 협의 안건으로 보고한다.

## 3. 문서 진입점

- [docs/frontend/INDEX.md](../docs/frontend/INDEX.md)

## 4. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 결정·미결 | [docs/frontend/decisions.md](../docs/frontend/decisions.md) |
| 설계·As-Is | [docs/frontend/module.md](../docs/frontend/module.md) |
| 합격 기준 | [docs/frontend/test-spec.md](../docs/frontend/test-spec.md) |
| 실행 결과 | [docs/frontend/regression-log.md](../docs/frontend/regression-log.md) |
| 진행 상태 | [docs/frontend/PROGRESS.md](../docs/frontend/PROGRESS.md) |

## 5. 모듈 경계와 개발 규칙

- Auth Guard는 화면 접근 제어일 뿐 보안 경계가 아니다. 인증·인가 판단을 프론트 코드에만 두지 않는다([FE-D012](../docs/frontend/decisions.md#1-확정-결정)).
- `axios`는 공통 API Client 안에서만 import하고, 원본 Axios 인스턴스를 밖에 노출하지 않는다. 요청은 공통 Client를 거친다
  ([FE-D007](../docs/frontend/decisions.md#1-확정-결정), [FE-D013](../docs/frontend/decisions.md#1-확정-결정)).
- 모든 요청에 인증 영향 범위를 지정한다. 기본값을 두지 않는다([FE-D010](../docs/frontend/decisions.md#1-확정-결정)).
- 운영 코드는 `src/test`를 직접·간접으로 import하지 않는다. 가안 Mock은 테스트에서만 쓰고, 환경변수로 켜는 경로를 두지 않는다
  ([FE-D005](../docs/frontend/decisions.md#1-확정-결정)).
- `VITE_`로 시작하는 환경변수는 번들에 들어간다. 비밀 값과 서버 측 설정을 넣지 않는다([FE-D002](../docs/frontend/decisions.md#1-확정-결정)).
- 의존성은 정확한 버전과 lockfile로 관리하고 재현 설치는 `npm ci`로 한다. `npm audit fix`, `--force`, `--legacy-peer-deps`는 승인
  없이 쓰지 않는다([FE-D008](../docs/frontend/decisions.md#1-확정-결정)).

## 6. 미정 규칙 상태

- Backend 인증 계약 연동 규칙 — [FE-U02](../docs/frontend/decisions.md#2-미결) 확정 뒤 정한다.
- XSRF 활성화 규칙 — [FE-U04](../docs/frontend/decisions.md#2-미결)([G-U02](../docs/policy.md#4-공통-미결)) 확정 뒤 정한다.
