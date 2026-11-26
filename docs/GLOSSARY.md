# GLOSSARY

두 모듈 이상이 쓰는 용어의 원본이다. 한 모듈에서만 쓰는 용어는 그 모듈 `CONTEXT.md` 4장에 둔다.
작성 규칙: [docs/INDEX.md 4장](INDEX.md#4-작성-규칙)

## 1. 공통 용어

| 용어 | 뜻 | 쓰는 모듈 |
| --- | --- | --- |
| 로그인 세션(login session) | 로그인을 마친 회원의 인증 상태 한 건. sessionId로 식별하며, 서버의 허용 목록에 있을 때만 유효하다. 서블릿 HttpSession과 다른 개념이다 | global(검증 계약), member(발급·저장) |
