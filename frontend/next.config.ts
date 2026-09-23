import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 도커 이미지에 node_modules 전체를 넣지 않기 위해 필요한 것만 추려 낸다.
  // .next/standalone 에 server.js 와 실제로 쓰는 모듈만 담긴다.
  output: "standalone",
};

export default nextConfig;
