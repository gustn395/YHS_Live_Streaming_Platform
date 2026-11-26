# member — test-spec

요구사항·합격 기준(`MB-T###`)의 원본이다. 작성 규칙: [docs/INDEX.md 4장](../INDEX.md#4-작성-규칙)

## 1. 요구사항과 합격 기준

명세 열은 Member 기준 명세의 기능 번호(M-xx)와 테스트 번호(MEM-xx)다([MB-D001](decisions.md#1-확정-결정)).

| ID | 요구사항 | 합격 기준 | 명세 | 근거 | 테스트 |
| --- | --- | --- | --- | --- | --- |
| MB-T001 | 정상 회원가입 | 201, `accountStatus=PENDING`. DB에 LOCAL·PENDING·`email_verified=false`·USER, username·이메일 소문자, 닉네임 앞뒤 공백 제거. `Set-Cookie`·토큰 없음 | M-01, MEM-01 | MB-D003, MB-D008 | `SignupApiIntegrationTest`, `MemberTest` |
| MB-T002 | 중복 username | 409 `MEMBER_USERNAME_DUPLICATE`(대소문자만 다른 경우 포함), 저장 건수 1 | M-01, MEM-02 | MB-D008 | `SignupApiIntegrationTest`, `MemberRepositoryAdapterTest` |
| MB-T003 | 중복 이메일(LOCAL끼리) | 409 `MEMBER_EMAIL_DUPLICATE`, 저장 건수 1. GOOGLE 계정과는 같은 이메일 허용 | M-01, M-10, MEM-09 | MB-D008 | `SignupApiIntegrationTest`, `MemberRepositoryAdapterTest` |
| MB-T004 | 동시 가입 | 같은 username 또는 같은 이메일로 8건 동시 요청 → 201 1건, 나머지 409, DB 1건. DB 제약 위반 상세(이메일 등)가 로그에 남지 않음 | M-01 | MB-D008 | `SignupApiIntegrationTest` |
| MB-T005 | 입력 형식 검증 | username·이메일·닉네임(길이·제어 문자) 오류, 닉네임의 Cc·Cf 문자(U+0085·U+202E·U+200B), 닉네임 길이는 code point 기준(이모지 20자 허용·21자 거부), 소문자로 바꾸면 ASCII가 되는 비ASCII username(U+212A KELVIN SIGN) 거부, 비밀번호 확인 불일치 → 400 `COMMON_INVALID_INPUT` + 필드별 `errors[]`, 저장 없음 | M-01, MEM-03 | MB-D008, G-D003 | `SignupApiIntegrationTest` |
| MB-T006 | 비밀번호 정책 | NFC 뒤 code point 15~64자, 공백 유지·Trim 없음, username·서비스 이름 파생어 거부 → 400 `errors[].field=password`. 응답·로그에 비밀번호 원문 없음 | M-02 | MB-D004, MB-D005 | `PasswordPolicyTest`, `SignupApiIntegrationTest` |
| MB-T007 | 비밀번호 해시 | 포맷·Salt 16바이트·해시 256비트, 같은 비밀번호도 해시가 다름, NFC·NFD 동일 처리, Trim 없음, 다른 pepper·키 ID·잘못된 포맷은 불일치, 반복 횟수 변경 시 재해시 대상. DB에 원문 없음 | M-02, MEM-04 | MB-D006 | `Pbkdf2PasswordHasherTest`, `SignupApiIntegrationTest` |
| MB-T008 | pepper 키 기동 검증 | 키 누락·키 ID 누락·256비트 아님·Base64 아님이면 기동 실패, 오류 메시지에 키 값 없음, 기본 반복 횟수 600,000 | M-02 | MB-D007 | `MemberPasswordPropertiesTest` |
| MB-T009 | ~~차단 목록 로딩~~ | 폐지: 로컬 유출 목록을 두지 않는다(MB-D010). 유출 검사는 MB-T012~MB-T017 | — | MB-D010 | — |
| MB-T010 | 스키마 | Flyway 마이그레이션 적용 + Hibernate 검증 통과, LOCAL 이메일 부분 유니크, CHECK(LOCAL 자격 증명·계정 상태), OAuth 연결 UNIQUE, 그 밖의 제약 위반은 409로 바꾸지 않음, 생성·수정 시각 자동 기록 | 3장 | MB-D008, G-D010 | `MemberRepositoryAdapterTest` |
| MB-T011 | Swagger Spec 일치 | `/v3/api-docs`의 회원가입: 태그 Authentication, 201·400·409·503(오류는 ProblemDetail 참조), 요청·응답 필드 | 4.6, MEM-22 | G-D008 | `SignupOpenApiIntegrationTest` |
| MB-T012 | HIBP 요청 범위 | `password` → `GET /range/5BAA6`(SHA-1 앞 5자만), `Add-Padding: true`·User-Agent 전송. 경로·헤더에 원문·전체 해시 없음 | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T013 | HIBP 응답 판정 | 일치 접미사(대소문자 무시)의 건수 1 이상 → 유출, 건수 0(패딩)만 일치 → 통과, 일치 없음 → 통과 | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T014 | HIBP 정규화 | NFC·NFD 입력이 같은 앞 5자로 조회 | M-02 | MB-D004, MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T015 | HIBP 비정상 응답 | 200 빈 본문, 형식 오류 줄, 숫자가 아닌 건수, 길이가 틀린 접미사, 301, 503, 429, 읽기 시간 초과, 연결 실패, 잘못된 base-url(요청 생성 실패) → 모두 확인 불가(통과로 보지 않음) | M-02 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T016 | HIBP 정보 비노출 | MB-T015 각 경우 로그에 원문·전체 SHA-1·앞 5자·`/range/` 없음 | M-02, 5장 | MB-D010 | `HibpPwnedPasswordsClientTest` |
| MB-T017 | 가입 API의 유출 검사 | 유출 → 400 `MEMBER_PASSWORD_BREACHED`, 확인 불가 → 503 `MEMBER_PASSWORD_CHECK_UNAVAILABLE`(둘 다 고정 detail, `errors[]` 없음), 두 경우 DB 0건·응답과 로그에 비밀번호 없음. 실제 HIBP 호출 없음(대역) | M-01, M-02 | MB-D010 | `SignupServiceTest`, `SignupApiIntegrationTest` |

## 2. 집계

- 요구사항 17건(MB-T001~MB-T017, MB-T009 폐지). JUnit 테스트: member 54건(단위 46, 영속성 8), app 14건(`SignupApiIntegrationTest` 13, `SignupOpenApiIntegrationTest` 1). 실행 결과는 [regression-log.md](regression-log.md).
- 아직 없는 명세 테스트: MEM-05~MEM-08, MEM-10~MEM-21, MEM-23(BE-03~BE-05).
