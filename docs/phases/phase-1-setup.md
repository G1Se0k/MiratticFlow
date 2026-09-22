# Phase 1 — 프로젝트 초기화

## 범위

frontend / backend 분리, MySQL 연결, Docker Compose 구성.

## 구성

```
backend/    Spring Boot 4.1.1 / Java 21 / Maven
frontend/   Next.js 16 / React 19 / TypeScript / Tailwind CSS 4
docker-compose.yml   MySQL 8.4 (named volume + healthcheck)
```

## 결정

### Gradle 대신 Maven

기획안에 빌드 툴 지정이 없었고, start.spring.io 의 Gradle 프로젝트 생성이
서버 오류(500)로 동작하지 않았다. Maven 프로젝트는 정상 생성되어 그대로 사용했다.
Maven Wrapper(`mvnw`)를 커밋해 두어 로컬에 Maven 설치가 없어도 실행된다.

### Docker Compose 는 MySQL 만

개발 중에는 프론트와 백엔드를 IDE 에서 직접 실행하는 편이 재시작이 빠르다.
애플리케이션 컨테이너화는 실제로 필요해지는 Phase 11(배포)에서 한다.

### 의존성은 Phase 에서 필요할 때 추가

초기 백엔드 의존성은 web, data-jpa, mysql, validation, lombok 다섯 개다.
Security 와 WebSocket 은 해당 Phase 에서 추가했다. 쓰지 않는 기술을 미리 넣으면
설정만 남고 이유를 설명할 수 없게 된다(기획안 §17).

## 설정

- `spring.jpa.open-in-view=false` — 지연 로딩이 뷰 렌더링 단계까지 끌려가지 않도록 끈다.
  트랜잭션 안에서 필요한 데이터를 다 읽고 DTO 로 변환한다.
- `spring.jpa.hibernate.ddl-auto=update` — 개발 중 스키마 자동 반영.
  운영에서는 쓰지 않는다(Phase 11 에서 정리).
- DB 접속 정보와 JWT secret 은 모두 환경변수로 덮어쓸 수 있게 `${VAR:기본값}` 형태로 둔다.

## 막혔던 것

**Spring Initializr 의 boot 버전 표기.** 메타데이터가 `4.1.1.RELEASE` 를 주지만
Maven Central 의 실제 아티팩트 버전은 `4.1.1` 이다. 생성된 `pom.xml` 을 그대로 쓰면
parent POM 을 찾지 못해 빌드가 실패한다. 수동으로 수정했다.

## 검증

- `cd backend && ./mvnw compile` 통과
- `cd frontend && npm run build` 통과
