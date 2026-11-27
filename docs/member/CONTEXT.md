# member — CONTEXT

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 모듈 역할

- 역할 원문: [backend/README.md 5장](../../backend/README.md#5-모듈과-패키지-규칙)의 `member/` 항목과
  [modules/member/build.gradle.kts](../../backend/modules/member/build.gradle.kts) 주석(회원가입, 로그인, OTP, OAuth, 회원 정보).

## 2. 액터

- 방문자: 아직 회원이 아니거나 로그인하지 않은 사용자. 회원가입·로그인을 한다.
- 회원: 가입한 사용자. 로그인(BE-03)으로 토큰을 받는다. 자기 정보를 조회하고 닉네임을 바꾼다(MB-W15).

## 3. 유스케이스

- 일반 회원가입(MB-W04): 방문자가 username·비밀번호·이메일·닉네임으로 가입하면 PENDING 회원이 생긴다([MB-D003](decisions.md#1-확정-결정)). 토큰은 발급하지 않는다.
- 로그인(MB-W06~W08, BE-03): 방문자가 username·비밀번호로 1차 인증을 통과하면 OTP Challenge가 생기고 이메일로 OTP를 받는다. OTP를 확인하면 로그인 세션과 토큰이 발급되고, PENDING 회원은 ACTIVE가 된다([MB-D011](decisions.md#1-확정-결정), [MB-D015](decisions.md#1-확정-결정)).
- Refresh(MB-W09, BE-04): 회원이 Refresh Token으로 같은 로그인 세션에서 새 Access·Refresh Token을 받는다. 로그인 세션의 절대 만료 시각(최초 로그인 + 7일)은 그대로이고 늘어나지 않는다. Refresh마다 회원 상태를 다시 확인한다([MB-D020](decisions.md#1-확정-결정), [MB-D021](decisions.md#1-확정-결정)).
- 로그아웃(MB-W10, BE-04): 회원이 이 브라우저가 쥔 로그인 세션을 서버에서 폐기하고 인증 쿠키를 지운다([MB-D022](decisions.md#1-확정-결정)).
- 내 정보 조회·수정(MB-W15, BE-07): 로그인한 회원이 자기 정보를 보고 닉네임을 바꾼다. 정지된 회원은 쓸 수 없고 그 세션은 폐기된다([MB-D028](decisions.md#1-확정-결정)). 다른 모듈은 공개 인터페이스로 회원 요약만 조회한다([MB-D029](decisions.md#1-확정-결정)).
- Google 로그인(MB-W11, BE-05): 방문자가 Google 인증(OIDC)을 1차 인증으로 통과하면 LOCAL과 같이 OTP Challenge가 생기고 이번 Google 이메일로 OTP를 받는다. 처음이면 PENDING GOOGLE 회원이 자동으로 생긴다. OTP를 확인하면 로그인 세션과 토큰이 발급되고 PENDING 회원은 ACTIVE가 된다([MB-D023](decisions.md#1-확정-결정)~[MB-D025](decisions.md#1-확정-결정)).

## 4. 모듈 용어

| 용어 | 뜻 |
| --- | --- |
| 회원(Member) | 내부 회원 ID(memberId)로 식별되는 사용자. 가입 방식과 관계없이 하나의 식별 단위다 |
| 계정 유형(account type) | 회원이 어떤 방식으로 가입했는지: LOCAL(username·비밀번호) / GOOGLE(OAuth) |
| 계정 상태(account status) | PENDING(가입 직후, 첫 OTP 전) / ACTIVE(사용 가능) / DISABLED(사용 중지) |
| 이메일 확인 여부(email_verified) | 이메일 소유가 확인됐는지. 계정 상태와 별개의 값이다. 일반 가입은 미확인 PENDING으로 시작하고, 첫 OTP 성공 때 확인됨·ACTIVE가 된다 |
| 문맥 단어(context word) | 비밀번호로 쓸 수 없는 서비스 이름, username과 그 파생어 |
| 유출 비밀번호 검사(breached password check) | 비밀번호가 알려진 유출 자료에 있는지 HIBP Pwned Passwords로 확인하는 것. 결과는 유출·통과·확인 불가 셋이다 |
| pepper | 비밀번호 해시에 한 번 더 쓰는 서버 비밀 키. DB 밖(환경변수)에 둔다 |
| 1차 인증(primary authentication) | 로그인에서 본인을 처음 확인하는 단계: LOCAL은 username·비밀번호, GOOGLE은 Google 인증(OIDC). 성공해도 로그인이 끝난 것이 아니며 토큰을 주지 않는다 |
| OTP Challenge | 1차 인증을 통과하고 OTP 확인을 기다리는 상태. 불투명 난수로 식별하고, 이것으로는 보호 API를 쓸 수 없다 |
| OTP | 이메일로 보내는 4자리 일회용 확인 번호. 한 Challenge 안에서 재발송하면 이전 OTP는 무효가 된다 |
| Refresh Token 교체(Rotation) | Refresh할 때마다 새 Refresh Token을 주고 이전 토큰을 다시 쓸 수 없게 하는 것. 로그인 세션(sid)과 절대 만료 시각은 그대로다 |
| 재사용 탐지(reuse detection) | 이미 교체된(사용된) Refresh Token이 다시 제출되면 탈취 가능성으로 보고 그 로그인 세션 전체를 폐기하는 것 |
| OAuth 연결(OAuth account link) | GOOGLE 회원과 외부 공급자 계정을 잇는 기록. 공급자(GOOGLE)와 공급자 사용자 ID(OIDC sub)로 회원 하나를 가리킨다 |
| OIDC sub | 공급자가 사용자마다 바꾸지 않고 주는 고유 ID. 이메일은 바뀔 수 있으므로 GOOGLE 회원은 sub로 찾는다 |
| OAuth 임시 인증 상태 | Google로 갔다가 돌아오는 동안만 쓰는 일회용 기록(state 등). 시작한 브라우저와 묶여 있고 한 번만 쓸 수 있다 |
