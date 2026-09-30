import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { FlowIssueDemo } from '@/components/landing/FlowIssueDemo';
import { DashboardMock, IssueListMock } from '@/components/landing/Mockups';
import { StartLink } from '@/components/landing/StartLink';
import { Logo } from '@/components/ui/Logo';

export const metadata: Metadata = {
  title: 'Mirattic Flow — 이슈와 대화를 한 화면에서',
  description: '워크스페이스 안에서 프로젝트와 이슈를 관리하고, 이슈 옆 실시간 채팅과 알림으로 소통하는 팀 협업 도구.',
};

/**
 * flow.mirattic.com 첫 화면(소개). 누구나 볼 수 있고, 로그인한 사람에게는 시작 버튼이 워크스페이스로 간다 (StartLink).
 * 색 · 모서리는 로그인 화면처럼 Mirattic/DESIGN .md 를 따른다 (이 페이지에만: 앱 안쪽은 앱 토큰 그대로).
 * 앱 화면을 흉내 낸 그림은 components/landing — 앱이 바뀌면 같이 고친다.
 */
export default function LandingPage() {
  return (
    <div className="min-h-dvh bg-white text-[16px] leading-[1.6] text-[#191f28] dark:bg-[#101318] dark:text-[#f2f4f6]">
      <header className="mx-auto flex h-16 max-w-[70rem] items-center px-4 sm:px-6">
        <Logo />
        <span className="ml-auto">
          <StartLink variant="quiet" />
        </span>
      </header>

      <main>
        <div className="mx-auto grid max-w-[70rem] items-center gap-12 px-4 pb-20 pt-10 sm:px-6 lg:grid-cols-[5fr_7fr] lg:gap-14 lg:pt-16">
          <div>
            <h1 className="text-[clamp(34px,5vw,54px)] font-bold leading-[1.18] tracking-[-0.035em]">
              이슈와 대화를
              <br />한 화면에서.
            </h1>
            <p className="mt-5 max-w-[30em] text-[18px] text-[#4e5968] dark:text-[#b0b8c1]">
              할 일은 이슈로 나누고, 이야기는 그 이슈 옆 채팅에서. 누가 무엇을 언제까지 하는지와 왜 그렇게 정했는지가 한곳에
              남습니다.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-4">
              <StartLink />
              <span className="text-[14px] text-[#4e5968] dark:text-[#b0b8c1]">무료 · Mirattic 계정 하나로</span>
            </div>
          </div>
          <FlowIssueDemo />
        </div>

        <Feature
          label="이슈 관리"
          title={<>워크스페이스 · 프로젝트 · 이슈</>}
          points={[
            ['팀마다 워크스페이스', '초대 링크로 멤버를 부르고, 프로젝트마다 이슈를 모읍니다.'],
            ['한눈에 보이는 이슈', '상태 · 담당자 · 마감일이 목록에서 바로 보이고, 급한 이슈에는 우선순위가 붙습니다.'],
            ['남는 결정', '이슈마다 설명과 댓글, 주제별 채팅이 함께 남습니다.'],
          ]}
          visual={<IssueListMock />}
        />
        <Feature
          flip
          label="알림과 대시보드"
          title={
            <>
              내 담당과 진행 상황을
              <br />
              놓치지 않게.
            </>
          }
          points={[
            ['알림', '나에게 배정되거나 상태가 바뀌거나 댓글이 달리면 알려 줍니다.'],
            ['대시보드', '프로젝트의 전체 · 진행 중 · 완료 · 내 담당 이슈를 숫자로 봅니다.'],
            ['실시간 채팅', '이슈 채팅과 프로젝트 채팅은 새로고침 없이 바로 오갑니다.'],
          ]}
          visual={<DashboardMock />}
        />

        <section className="border-t border-[#e5e8eb] dark:border-[#2c323b]">
          <div className="mx-auto grid max-w-[70rem] gap-10 px-4 py-20 sm:px-6 lg:grid-cols-[5fr_7fr] lg:gap-14">
            <div>
              <p className="text-[14px] font-semibold text-[#4e5968] dark:text-[#b0b8c1]">Mirattic 계정</p>
              <h2 className="mt-3 text-[clamp(26px,3.2vw,36px)] font-bold leading-[1.25] tracking-[-0.03em]">
                계정 하나로 시작.
              </h2>
            </div>
            <ul className="divide-y divide-[#e5e8eb] dark:divide-[#2c323b]">
              <Point>이메일, 카카오, 네이버, Google 중 편한 방법으로 가입합니다. Mirattic Sync에도 같은 계정으로 들어갑니다.</Point>
              <Point>비밀번호는 Mirattic 계정에서만 입력합니다. Flow는 비밀번호를 받지도, 저장하지도 않습니다.</Point>
              <Point>
                로그인된 기기와 탈퇴는{' '}
                <a href="https://auth.mirattic.com/account" className="text-[#1b64da] underline underline-offset-4 dark:text-[#9cc2ff]">
                  내 계정
                </a>
                에서 직접 관리합니다.
              </Point>
            </ul>
          </div>
        </section>

        <section className="border-t border-[#e5e8eb] px-4 py-24 text-center dark:border-[#2c323b]">
          <h2 className="text-[clamp(26px,3.2vw,36px)] font-bold leading-[1.25] tracking-[-0.03em]">
            오늘 할 일, 팀과 같이 정리하세요.
          </h2>
          <p className="mb-8 mt-3 text-[#4e5968] dark:text-[#b0b8c1]">워크스페이스를 만들고 팀원을 초대하면 끝입니다.</p>
          <StartLink />
        </section>
      </main>

      <footer className="border-t border-[#e5e8eb] dark:border-[#2c323b]">
        <div className="mx-auto flex max-w-[70rem] flex-wrap gap-x-5 gap-y-2 px-4 py-7 text-[13px] text-[#4e5968] sm:px-6 dark:text-[#b0b8c1]">
          <span>© 2026 Mirattic Flow</span>
          <Link href="/terms" className="hover:underline">
            이용약관
          </Link>
          <Link href="/privacy" className="hover:underline">
            개인정보처리방침
          </Link>
          <a href="https://mirattic.com" className="hover:underline">
            Mirattic
          </a>
        </div>
      </footer>
    </div>
  );
}

function Feature({
  label,
  title,
  points,
  visual,
  flip = false,
}: {
  label: string;
  title: ReactNode;
  points: [string, string][];
  visual: ReactNode;
  flip?: boolean;
}) {
  return (
    <section className="border-t border-[#e5e8eb] dark:border-[#2c323b]">
      <div
        className={`mx-auto grid max-w-[70rem] items-center gap-10 px-4 py-20 sm:px-6 lg:gap-14 ${flip ? 'lg:grid-cols-[7fr_5fr]' : 'lg:grid-cols-[5fr_7fr]'}`}
      >
        <div className={flip ? 'lg:order-2' : ''}>
          <p className="text-[14px] font-semibold text-[#4e5968] dark:text-[#b0b8c1]">{label}</p>
          <h2 className="mt-3 text-[clamp(26px,3.2vw,36px)] font-bold leading-[1.25] tracking-[-0.03em]">{title}</h2>
          <ul className="mt-6 divide-y divide-[#e5e8eb] border-y border-[#e5e8eb] dark:divide-[#2c323b] dark:border-[#2c323b]">
            {points.map(([head, body]) => (
              <li key={head} className="py-3 text-[#4e5968] dark:text-[#b0b8c1]">
                <b className="font-semibold text-[#191f28] dark:text-[#f2f4f6]">{head}</b> — {body}
              </li>
            ))}
          </ul>
        </div>
        {visual}
      </div>
    </section>
  );
}

function Point({ children }: { children: ReactNode }) {
  return <li className="py-3.5 first:pt-0">{children}</li>;
}
