import type { Metadata } from 'next';
import localFont from 'next/font/local';
import './globals.css';
import { Providers } from './providers';

/**
 * Pretendard (OFL 1.1, fonts/LICENSE.txt).
 * 전체 버전은 2MB 라 KS X 1001 서브셋(285KB)을 쓴다. 여기에 없는 드문 음절은
 * 시스템 한글 폰트로 떨어진다 — 첫 화면 2MB 를 받는 것보다 낫다.
 */
const pretendard = localFont({
  src: './fonts/PretendardStdVariable.woff2',
  weight: '45 920',
  display: 'swap',
  variable: '--font-pretendard',
});

export const metadata: Metadata = {
  title: 'Mirattic Flow',
  description: '팀 협업 · 이슈 관리 서비스',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={pretendard.variable}>
      <body className="font-sans antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
