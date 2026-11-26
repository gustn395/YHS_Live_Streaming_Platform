# member — CONTEXT

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 모듈 역할

- 역할 원문: [backend/README.md 5장](../../backend/README.md#5-모듈과-패키지-규칙)의 `member/` 항목과
  [modules/member/build.gradle.kts](../../backend/modules/member/build.gradle.kts) 주석(회원가입, 로그인, OTP, OAuth, 회원 정보).

## 2. 액터

- 방문자: 아직 회원이 아니거나 로그인하지 않은 사용자. 회원가입·로그인을 한다.
- 회원: 가입한 사용자. 로그인(BE-03)으로 토큰을 받는다. 회원 정보 조회는 이후 작업.

## 3. 유스케이스

- 일반 회원가입(MB-W04): 방문자가 username·비밀번호·이메일·닉네임으로 가입하면 PENDING 회원이 생긴다([MB-D003](decisions.md#1-확정-결정)). 토큰은 발급하지 않는다.
- 로그인(MB-W06~W08, BE-03): 방문자가 username·비밀번호로 1차 인증을 통과하면 OTP Challenge가 생기고 이메일로 OTP를 받는다. OTP를 확인하면 로그인 세션과 토큰이 발급되고, PENDING 회원은 ACTIVE가 된다([MB-D011](decisions.md#1-확정-결정), [MB-D015](decisions.md#1-확정-결정)).
- Refresh·로그아웃: BE-04. Google 로그인: BE-05.

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
| 1차 인증(primary authentication) | 로그인에서 username·비밀번호를 확인하는 단계. 성공해도 로그인이 끝난 것이 아니며 토큰을 주지 않는다 |
| OTP Challenge | 1차 인증을 통과하고 OTP 확인을 기다리는 상태. 불투명 난수로 식별하고, 이것으로는 보호 API를 쓸 수 없다 |
| OTP | 이메일로 보내는 4자리 일회용 확인 번호. 한 Challenge 안에서 재발송하면 이전 OTP는 무효가 된다 |
