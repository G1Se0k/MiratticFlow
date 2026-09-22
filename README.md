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

## 진행 상황

- [x] Phase 1 — 프로젝트 초기화
- [x] Phase 2 — 인증 (JWT)
- [ ] Phase 3 — 워크스페이스
- [ ] Phase 4 — 프로젝트
- [ ] Phase 5 — 이슈
- [ ] Phase 6 — 댓글
- [ ] Phase 7 — WebSocket 채팅
- [ ] Phase 8 — 알림
- [ ] Phase 9 — 대시보드
- [ ] Phase 10 — 품질 개선
- [ ] Phase 11 — 배포
