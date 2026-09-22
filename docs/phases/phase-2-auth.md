# Phase 2 — 인증 (JWT)

백엔드(2-A)와 프론트엔드(2-B)로 나눠 진행했다.

## 토큰 전략

| | 수명 | 저장 위치 | 용도 |
| --- | --- | --- | --- |
| Access Token | 15분 | 클라이언트 localStorage | 모든 API 요청의 `Authorization: Bearer` |
| Refresh Token | 14일 | 클라이언트 localStorage + **서버 DB** | Access 재발급 |

### Refresh Token 을 DB 에 저장한 이유

JWT 는 서명만으로 검증되므로 서버가 아무것도 저장하지 않으면 만료 전까지 회수할 방법이 없다.
로그아웃과 탈취 대응을 하려면 "지금 유효한 refresh 목록"이 서버에 있어야 한다.

선택지는 세 가지였다.

- **Redis** — 빠르고 TTL 이 자동이지만 인프라가 하나 늘어난다.
- **MySQL** — 이미 쓰고 있다. 재발급은 15분에 한 번뿐이라 성능이 문제되지 않는다.
- **저장 안 함** — 로그아웃이 "클라이언트에서 토큰 버리기"에 그친다. 토큰이 유출되면 막을 수 없다.

MySQL 을 선택했다. 쓰지 않을 기술을 늘리지 않는다는 기준(기획안 §17)에 맞고,
호출 빈도가 낮아 DB 조회 비용이 실질적인 부담이 되지 않는다.

### localStorage 에 저장한 이유와 그 대가

HttpOnly 쿠키가 XSS 에 더 안전하지만, CORS credentials 와 SameSite 설정이 따라붙는다.
이 프로젝트는 구현 단순성을 택했고, 그 결과 두 가지 대가를 진다.

1. XSS 가 발생하면 토큰 두 개 모두 탈취된다.
2. 서버(Next.js 미들웨어)가 토큰을 읽을 수 없어 **라우트 보호를 클라이언트에서만 할 수 있다.**

2번은 설계상 문제가 아니다. 클라이언트 가드는 화면을 가리는 UX 장치이고,
실제 인가는 백엔드가 매 요청 토큰을 검증해서 한다. 가드를 우회해도 API 가 401 을 낸다.

## 백엔드 구조

```
global/entity/BaseTimeEntity          createdAt / updatedAt 자동 기록
global/response/ErrorCode             에러 코드·메시지·HTTP status 를 한 곳에서 관리
global/response/ErrorResponse         전체가 공유하는 단일 에러 포맷
global/exception/BusinessException, GlobalExceptionHandler
global/config/JpaConfig               @EnableJpaAuditing
global/security/JwtProvider           토큰 생성·검증·파싱
                JwtAuthenticationFilter    요청당 1회, SecurityContext 에 인증 주입
                SecurityConfig             STATELESS, CSRF off, CORS
                SecurityExceptionHandlers  필터 단계의 401/403 을 같은 포맷으로
                AuthUser                   인증된 사용자 (id, email)
user/     User, UserRepository, UserResponse, UserController(/me)
auth/     RefreshToken, DTO 4종, AuthService, AuthController
```

### 필터 체인

```
요청 → JwtAuthenticationFilter (토큰 있으면 검증 후 SecurityContext 에 저장, 없어도 통과)
     → authorizeHttpRequests (permitAll 외에는 인증 요구)
     → 인증 실패 시 AuthenticationEntryPoint → 401
     → Controller
```

필터가 직접 막지 않고 통과시키는 이유: 인증이 필요 없는 경로도 같은 필터를 지나기 때문이다.
"토큰이 유효하면 인증 정보를 넣는다"까지만 하고, 막는 판단은 뒤쪽 설정에 맡긴다.

### API

```
POST /api/auth/signup    201  인증 불필요
POST /api/auth/login     200  { accessToken, refreshToken }
POST /api/auth/reissue   200  { accessToken, refreshToken }  기존 refresh 는 폐기
POST /api/auth/logout    204  해당 refresh 삭제
GET  /api/users/me       200  인증 필요
```

## 설계에서 바꾼 것

### CustomUserDetailsService 를 만들지 않았다

처음 계획에는 있었지만 쓸 데가 없었다.

- 로그인은 `UserRepository` + `PasswordEncoder` 로 직접 검증하면 된다.
- 인증된 요청은 토큰 안의 `sub`, `email` 만으로 충분하다. **요청마다 DB 를 조회하지 않는다.**

`AuthenticationManager` 설정과 클래스 두 개가 사라졌다.
"stateless 인증이므로 매 요청 DB 조회가 필요 없다"는 쪽이 설명하기도 쉽다.

### 성공 응답에 래퍼를 씌우지 않았다

성공과 실패는 HTTP 상태 코드가 이미 구분한다. 포맷 통일이 실제로 필요한 쪽은 에러다.
성공 응답은 DTO 를 그대로 반환하고, 에러만 `ErrorResponse` 하나로 모았다.

### 로그아웃을 인증 없이 호출 가능하게 했다

access token 이 만료된 시점이야말로 로그아웃하는 때다. 인증을 요구하면 그때 로그아웃이 불가능해진다.
refresh token 을 아는 것 자체가 권한이고, 하는 일은 그 토큰 한 행을 지우는 것뿐이다.

## 프론트엔드 구조

```
src/lib/api/client.ts        fetch 래퍼 — 토큰 주입, 에러 파싱, 401 자동 재발급
src/lib/api/auth.ts types.ts
src/lib/auth/tokens.ts       localStorage 접근은 이 파일에서만
src/lib/validation/auth.ts   Zod 스키마 (백엔드 @Valid 와 동일 규칙)
src/hooks/useAuth.ts         useMe / useLogin / useSignup / useLogout
src/app/providers.tsx        QueryClientProvider
src/app/(auth)/              login, signup
src/app/(protected)/         인증 가드 layout + dashboard(임시)
src/components/ui/           Button, FormField, Spinner
```

### 로그인 여부를 별도 상태로 두지 않았다

`/api/users/me` 쿼리가 성공하는지로 판단한다. 전역 auth store 를 따로 만들면
서버가 아는 상태와 클라이언트가 믿는 상태가 반드시 어긋나는 순간이 온다.
TanStack Query 가 이미 캐시·로딩·에러를 관리하므로 Zustand 는 넣지 않았다.

### 클라이언트 검증은 서버 검증을 대체하지 않는다

Zod 스키마는 왕복을 줄이는 용도다. 실제 방어선은 백엔드의 `@Valid` 이고,
검증 실패 시 어떤 필드가 왜 틀렸는지 `ErrorResponse.errors` 로 내려온다.

## 트러블슈팅

### 1. 폐기한 Refresh Token 이 계속 통했다

**문제** — 재발급을 두 번 연속 호출했더니, 첫 번째에서 폐기됐어야 할 refresh token 으로
두 번째 재발급이 성공했다. rotation 이 동작하지 않았다.

**원인** — DB 를 열어보니 행은 정상적으로 삭제되고 있었다. 문제는 JWT 자체였다.
`createRefreshToken(userId, email)` 이 만드는 payload 는 `sub`, `email`, `iat`, `exp` 뿐이고,
`iat` 은 초 단위다. 같은 사용자가 **같은 초 안에** 재발급을 받으면 payload 가 완전히 같아져
새 토큰 문자열이 이전 것과 바이트 단위로 동일해진다.
결국 "이전 토큰을 지우고 똑같은 값을 다시 넣는" 동작이 되어 rotation 이 무의미했다.

**해결** — Refresh Token 에 `jti`(UUID) 클레임을 추가해 매번 다른 토큰이 되게 했다.
(`JwtProvider.createRefreshToken`)

**결과** — 재발급 시 새 토큰이 이전과 달라지고, 폐기된 토큰 재사용은 401 `INVALID_TOKEN` 이 된다.
회귀를 막기 위해 테스트 두 개를 남겼다.
- `AuthServiceTest` — 연달아 발급한 refresh token 이 서로 다른지
- `AuthApiTest` — 재발급에 쓴 토큰을 다시 쓰면 401 인지

시간 기반 값만으로 고유성을 기대하면 안 된다는 것, 그리고 "DB 에서 지웠으니 폐기됐다"는
가정이 값의 동일성 때문에 깨질 수 있다는 것을 확인한 사례다.

### 2. 동시 요청이 401 을 받으면 로그아웃돼 버린다

**문제** — 화면 진입 시 API 를 여러 개 동시에 호출하는 경우, access token 이 만료돼 있으면
모든 요청이 401 을 받고 각자 재발급을 시도한다.

**원인** — 백엔드가 refresh 를 rotation 하므로 **첫 번째 재발급만 성공한다.**
나머지는 이미 폐기된 토큰을 들고 요청하게 되어 실패하고, 실패 처리 로직이 토큰을 지워
결국 로그아웃된다. 재발급 정책(rotation)과 클라이언트 동시성이 충돌한 경우다.

**해결** — 진행 중인 재발급 Promise 하나를 모듈 스코프에 두고 공유한다.
먼저 도착한 요청이 재발급을 실행하고, 나머지는 같은 Promise 를 기다린 뒤 원래 요청을 재시도한다.
(`src/lib/api/client.ts`)

```ts
refreshPromise ??= refreshTokens().finally(() => { refreshPromise = null; });
const refreshed = await refreshPromise;
```

**결과** — 동시에 몇 개가 401 을 받아도 재발급은 한 번만 호출된다.
Phase 9 대시보드처럼 통계 API 를 여러 개 동시에 쏘는 화면에서 바로 드러날 문제를 미리 막았다.

## 검증

### 자동 테스트 (13개, `./mvnw test`)

`AuthServiceTest` — 회원가입 시 비밀번호 해시 저장 / 중복 이메일 / 로그인 성공 /
비밀번호 불일치 / 없는 이메일 / refresh token 고유성 / DB 에 없는 refresh 거부

`AuthApiTest` — 가입→로그인→`/me` 전체 흐름 / rotation / 로그아웃 후 재발급 차단 /
토큰 없이 접근 시 401 / validation 실패 시 필드별 사유

테스트는 H2 인메모리 DB(`application-test.properties` + `@ActiveProfiles("test")`)를 써서
Docker 없이도 실행된다. 데이터소스만 덮어쓰므로 나머지 설정은 운영과 같은 것을 쓴다.

### 수동 검증 (브라우저)

| 시나리오 | 결과 |
| --- | --- |
| 비밀번호 확인 불일치 | 서버 호출 없이 필드 에러 |
| 회원가입 성공 | `/login?signup=success` 이동 + 안내 |
| 비밀번호 틀림 | 서버 메시지 그대로 표시 |
| 로그인 성공 | `/dashboard`, 내 정보 렌더 |
| access token 만 손상 | 화면 유지, 두 토큰 모두 교체 (자동 재발급 + rotation) |
| refresh 까지 손상 | `/login` 이동, localStorage 비움 |
| 로그아웃 | `/login` 이동, 토큰 삭제, DB refresh 행도 삭제 확인 |

API 단위 확인은 `http/auth.http` (WebStorm HTTP Client) 에 13개 요청으로 정리해두었다.

## 알려진 한계

**만료된 refresh 행이 DB 에 쌓인다.** 클라이언트가 토큰을 잃어버린 경우(로컬 스토리지 초기화 등)
서버에 삭제를 요청할 수 없어 만료(14일)까지 행이 남는다.
정상 사용 경로에서는 로그아웃·재발급 시 항상 삭제되므로 문제되지 않는다.
양이 문제되면 정리 스케줄러를 넣는다 — `AuthService.issueTokens` 에 `ponytail:` 주석으로 표시.

**JWT secret 의 기본값이 저장소에 있다.** `application.properties` 의
`${JWT_SECRET:local-dev-secret-key-...}` 는 로컬 개발 편의를 위한 것이다.
배포 시 `JWT_SECRET` 환경변수를 반드시 주입해야 한다 — Phase 11 에서 처리.

## Spring Boot 4 / Next.js 16 에서 달랐던 점

작업 중 기존 자료와 달라 막혔던 부분들.

- **Jackson 3** — `com.fasterxml.jackson.databind.ObjectMapper` 가 아니라 `tools.jackson.databind.ObjectMapper`.
  애노테이션(`@JsonInclude` 등)은 `com.fasterxml.jackson.annotation` 에 그대로 있다.
- **`@AutoConfigureMockMvc`** — `org.springframework.boot.webmvc.test.autoconfigure` 로 이동.
- **테스트 스타터 분리** — `spring-boot-starter-test` 하나가 아니라
  `...-webmvc-test`, `...-data-jpa-test`, `...-security-test` 로 나뉘어 있다.
- **jjwt 0.13** — `ExpiredJwtException` 이 `JwtException` 의 하위 타입이라 multi-catch 에 함께 쓸 수 없다.
