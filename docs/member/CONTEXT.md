# member — CONTEXT

작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 모듈 역할

- 역할 원문: [backend/README.md 5장](../../backend/README.md#5-모듈과-패키지-규칙)의 `member/` 항목과
  [modules/member/build.gradle.kts](../../backend/modules/member/build.gradle.kts) 주석(회원가입, 로그인, OTP, OAuth, 회원 정보).

## 2. 액터

- 방문자: 아직 회원이 아니거나 로그인하지 않은 사용자. 회원가입을 한다.
- 회원: 가입한 사용자. 로그인·OTP·회원 정보 조회는 BE-03 이후.

## 3. 유스케이스

- 일반 회원가입(MB-W04): 방문자가 username·비밀번호·이메일·닉네임으로 가입하면 PENDING 회원이 생긴다([MB-D003](decisions.md#1-확정-결정)). 토큰은 발급하지 않는다.
- 로그인·OTP·토큰·Google 로그인: BE-03~BE-05.

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
