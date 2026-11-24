# YHS Live Streaming Platform

MediaMTX(WebRTC)와 Caddy를 기반으로 하는 라이브 스트리밍 플랫폼 프로젝트다.

## 폴더 구조

```
.
├── .github/workflows/        # GitHub Actions CI/CD 워크플로
├── backend/                  # 백엔드 애플리케이션 (Java/Spring)
├── frontend/                 # 프론트엔드 애플리케이션 (Node 기반)
├── poc/
│   └── mediamtx-webrtc/      # MediaMTX WebRTC 송출·재생 검증용 PoC
├── infrastructure/
│   ├── mediamtx/             # MediaMTX 설정
│   ├── caddy/                # Caddy(리버스 프록시·TLS) 설정
│   └── docker/               # Docker 및 Compose 설정
├── docs/
│   ├── architecture/         # 아키텍처 문서
│   ├── api/                  # API 명세
│   └── testing/              # 테스트 계획·결과
├── .env.example              # 환경변수 예시
├── .gitignore
└── README.md
```

빈 폴더는 Git이 추적하도록 `.gitkeep` 파일을 둔다. 폴더에 실제 파일이 생기면 `.gitkeep`은 지워도 된다.

## 로컬 환경변수 설정

```bash
cp .env.example .env
```

복사한 `.env`에 로컬 값을 채운다. `.env`는 `.gitignore`에 포함되어 커밋되지 않는다.
