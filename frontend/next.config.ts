import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 도커 이미지에 node_modules 전체를 넣지 않기 위해 필요한 것만 추려 낸다.
  // .next/standalone 에 server.js 와 실제로 쓰는 모듈만 담긴다.
  output: "standalone",
  // 개발 서버에서만: /api · /auth 를 백엔드로 넘겨 로그인 쿠키가 같은 출처(127.0.0.1:3000)에 붙게 한다.
  // 운영에서는 Caddy 가 같은 일을 한다 (이 경로들은 Next 까지 오지 않는다).
  async rewrites() {
    if (process.env.NODE_ENV === "production") return [];
    const backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/auth/:path*", destination: `${backend}/auth/:path*` },
    ];
  },
};

export default nextConfig;
