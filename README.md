# Mirattic Flow

팀 협업 · 이슈 관리 서비스. 워크스페이스 안에서 프로젝트와 이슈를 관리하고, 프로젝트별 채팅으로 실시간 소통합니다.

> 상세 기획은 [기획안.txt](기획안.txt) 참고.

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Maven |
| Database | MySQL 8.4 |
| Infra | Docker Compose |

## 구조

```
.
├── backend/    Spring Boot REST API
├── frontend/   Next.js 앱
└── docker-compose.yml   MySQL
```

## 실행

```bash
cp .env.example .env
docker compose up -d                    # MySQL

cd backend && ./mvnw spring-boot:run    # http://localhost:8080

cd frontend && cp .env.local.example .env.local && npm install && npm run dev   # http://localhost:3000
```

## 운영 배포 자동화

`main`에 푸시하거나 GitHub Actions의 `Deploy`를 수동 실행하면 홈 서버에서
소스를 갱신하고 Docker Compose로 빌드·재배포합니다.

- 서버 경로: `/Users/giseok/Documents/MiratticFlow`
- GitHub Actions 시크릿: `DEPLOY_SSH_KEY` (서버 SSH 접속용 개인 키)
- 서버 전용 파일: `.env.prod` (Git 추적 제외)
- 공용 Caddy 설정: `/opt/homebrew/etc/Caddyfile` (호스트에서 별도 관리)

최초 설정 시 `.env.prod.example`을 `.env.prod`로 복사해 설정합니다.
`Caddyfile.example`은 공용 호스트 Caddy에 추가할 도메인 설정 예시입니다.
Caddy는 Homebrew 서비스로 별도 실행하며, 프론트엔드 `127.0.0.1:3001`과
백엔드 `127.0.0.1:8080`으로 연결합니다. 앱 배포는 Caddy를 재시작하지 않습니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

배포는 fast-forward만 허용하며 서버의 로컬 변경을 강제로 초기화하지 않습니다.
컨테이너 기동 확인 이후에도 실제 도메인의 HTTPS 접속은 별도로 확인해야 합니다.

## 진행 상황

- [x] Phase 1 — 프로젝트 초기화
- [x] Phase 2 — 인증 (JWT)
- [x] Phase 3 — 워크스페이스
- [x] Phase 4 — 프로젝트
- [x] Phase 5 — 이슈
- [x] Phase 6 — 댓글
- [x] Phase 7 — WebSocket 채팅
- [x] Phase 8 — 알림
- [x] Phase 9 — 대시보드
- [x] Phase 10 — 품질 개선
- [x] Phase 11 — 배포
