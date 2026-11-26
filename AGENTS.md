# YHS Live Streaming Platform — AGENTS.md

이 저장소에서 사람과 AI 에이전트가 함께 따르는 프로젝트 공통 규칙이다. 모듈 전용 규칙은 `backend/modules/<module>/AGENTS.md`에
두고, 여기 있는 규칙을 반복하지 않는다. 문서 탐색은 [docs/INDEX.md](docs/INDEX.md)에서 시작한다.

## 원본 문서

정보마다 원본 문서가 하나씩 있다. 어느 정보를 어디서 찾고 고치는지는 [docs/INDEX.md 2장](docs/INDEX.md#2-원본-관계)이 정한다.

## 코드

- Spring Boot 4 계열로 실행한다(정확한 버전과 기술 스택은 [backend/README.md](backend/README.md) 첫머리 표가 원본). 비즈니스
  코드는 **Spring Boot 3.5.x / Spring Framework 6.2.x와 호환되는 API를 우선** 쓴다. Boot 4 / Framework 7 전용 API로 바꾸는 것을 기본으로 삼지 않고,
  3.5 호환 API를 낡은 코드로 보지 않는다. 빌드 설정의 Boot 4 전용 스타터 이름·플러그인은 예외다.
- 모듈 경계·계층 패키지(`in`·`app`·`domain`·`out`)·의존 방향·모듈 추가 절차는
  [backend/README.md 5장](backend/README.md#5-모듈과-패키지-규칙)을 따른다.
- 라이브러리는 [README 6장](backend/README.md#6-의존성-도입-계획-q3)의 도입 시점에 필요한 것만 추가한다. 버전은 Spring Boot BOM
  또는 `backend/gradle/libs.versions.toml`로만 관리하고, 저장소는 `backend/settings.gradle.kts`에서만 선언한다.
- 비밀번호·토큰·키는 기본값 없이 환경변수로만 받고 설정 파일·Git에 적지 않는다([README 4장](backend/README.md#4-설정과-환경변수)).
  이 저장소는 공개다.

## Git

- `main` + `feature/<Issue 번호>-<module 또는 영역>-<요지>`. Issue를 먼저 만들고 그 번호로 브랜치를 만든다
  ([Step 4.5 Q7](docs/testing/step4-5-agent-harness.md#1-결정)).
- `main`에는 PR로만, Squash Merge로 병합한다. Ruleset 결정은 [step4 문서](docs/testing/step4-ci-coderabbit.md) 1장 Q3, 적용 확인은
  같은 문서 4.1절이다. 필수 체크를 Gate `backend-ci`·`frontend-ci`로 바꾸는 전환·복구 절차와 적용 기록은 [FE-02 문서](docs/testing/fe02-ci-split.md)
  1장 Q5·5장이다.

## 정책과 결정

- 정해지지 않은 정책은 임의로 정하지 않는다. 결정이 필요하면 근거 문서와 선택지를 보이고 결정 주체에게 묻는다.
- 결정 원문은 원본 한 곳에만 둔다([docs/INDEX.md 2장](docs/INDEX.md#2-원본-관계)).
- 확정되지 않은 정책·구현하지 않은 기능·실행하지 않은 테스트를 적는 방법은 [docs/INDEX.md 4장](docs/INDEX.md#4-작성-규칙)을 따른다.
- 문서와 코드가 다르면 코드에 맞춰 정책을 바꾸지 않고 차이를 보고한다. 정책이 아직 구현되지 않은 목표일 수 있다.
