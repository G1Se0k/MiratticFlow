# Mirattic Flow

취업용 풀스택 포트폴리오. 팀 협업 · 이슈 관리 서비스.
전체 기획은 `기획안.txt` (이 파일보다 기획안이 우선).

## 구조

```
backend/    Spring Boot 4.1 / Java 21 / Maven   com.mirattic.flow
frontend/   Next.js 16 / React 19 / TS / Tailwind 4   src/app (App Router)
docker-compose.yml   MySQL 8.4
```

## 명령어

```bash
docker compose up -d                  # MySQL (먼저 실행해야 백엔드가 뜸)
cd backend && ./mvnw spring-boot:run  # :8080
cd backend && ./mvnw test
cd frontend && npm run dev            # :3000
cd frontend && npm run build          # 타입 체크 겸용
```

## 작업 원칙 (기획안 §15 — 가장 중요)

각 Phase 구현 **전에** 반드시 설명하고 사용자 확인을 받는다:
1. 어떤 구조로 구현할지  2. 파일 목록  3. 데이터 흐름
확인 후 구현, 구현 후 핵심 코드 설명.

복잡한 기술을 쓸 때는 왜 필요한지 / 다른 선택지 / 왜 이걸 골랐는지 설명한다.
면접에서 사용자가 직접 설명할 수 있는 수준의 코드만 쓴다.

## 하지 말 것 (기획안 §17)

과도한 추상화, 구현 하나짜리 인터페이스, 의미 없는 디자인 패턴,
안 쓰는 기술 추가, MSA/Kubernetes, 미완성 기능 늘리기.

## 백엔드 규칙

- 패키지는 도메인별: `auth user workspace project issue comment chat notification global`
  각 도메인 안에 `controller service repository entity dto`
- `global`: `config security exception response`
- 응답 포맷은 전체 통일. 에러는 `GlobalExceptionHandler`에서 한 곳으로 처리
- 쓰기 로직은 Service에 `@Transactional`, 조회는 `readOnly = true`
- `spring.jpa.open-in-view=false` — 지연 로딩은 트랜잭션 안에서 끝내고 DTO로 변환
- 연관관계는 기본 `LAZY`. 목록 조회는 N+1 확인 (fetch join / `@EntityGraph`)
- 엔티티를 컨트롤러 밖으로 내보내지 않는다 (요청·응답 모두 DTO)

## 프론트엔드 규칙

- 서버 상태는 TanStack Query, 폼은 React Hook Form + Zod
- API 호출은 한 곳(`src/lib/api`)으로 모으고 토큰 주입·401 처리도 거기서
- 로딩 / 에러 / Empty State는 화면마다 기본으로 챙긴다
- Next.js 16은 학습 데이터와 다르므로 `node_modules/next/dist/docs/` 확인 (`frontend/AGENTS.md`)

## Git

기능 단위로 커밋. `feat: implement user signup`, `fix: prevent non-member issue access` 형식.

## Phase 진행

1 초기화 ✅ → 2 인증(JWT) ✅ → 3 워크스페이스 → 4 프로젝트 → 5 이슈 →
6 댓글 → 7 WebSocket 채팅 → 8 알림 → 9 대시보드 → 10 품질 → 11 배포 → 12 포트폴리오 마무리

완료 시 `README.md`의 진행 상황 체크박스를 갱신한다.
