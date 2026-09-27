'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRef, useState, type ReactNode } from 'react';
import { NotificationBell } from '@/components/notification/NotificationBell';
import { Avatar } from '@/components/ui/Avatar';
import { Icon, type IconName } from '@/components/ui/Icon';
import { Logo } from '@/components/ui/Logo';
import { useLogout } from '@/hooks/useAuth';
import { useDismiss } from '@/hooks/useDismiss';
import { useIssue } from '@/hooks/useIssues';
import { useProject, useProjects } from '@/hooks/useProjects';
import { useWorkspaces } from '@/hooks/useWorkspaces';
import type { UserResponse } from '@/lib/api/types';

/**
 * 지금 보고 있는 워크스페이스를 URL 에서 찾는다.
 * 프로젝트·이슈 화면에는 워크스페이스 id 가 URL 에 없지만, 두 화면 모두 이미 상세를 조회하고 있어
 * 캐시에 들어 있는 값을 그대로 쓴다 (추가 요청이 생기지 않는다).
 */
function useActiveWorkspaceId(fallbackId: number) {
  const pathname = usePathname();
  const fromUrl = Number(pathname.match(/^\/workspaces\/(\d+)/)?.[1] ?? 0);
  const issueId = Number(pathname.match(/^\/issues\/(\d+)/)?.[1] ?? 0);
  const { data: issue } = useIssue(issueId);
  const projectId = Number(pathname.match(/^\/projects\/(\d+)/)?.[1] ?? 0) || (issue?.projectId ?? 0);
  const { data: project } = useProject(projectId);

  // 주소에 워크스페이스가 없는 화면(목록·계정)에서는 첫 워크스페이스를 펼쳐 둔다.
  // 사이드바가 빈 채로 남아 있으면 왼쪽 열이 있을 이유가 없다.
  return { workspaceId: fromUrl || project?.workspaceId || fallbackId, projectId };
}

/** 사이드바 본문. 데스크톱의 고정 열과 모바일의 슬라이드오버가 같은 것을 쓴다. */
export function SidebarContent({ user, onNavigate }: { user: UserResponse; onNavigate?: () => void }) {
  const pathname = usePathname();
  const logout = useLogout();
  const { data: workspaces } = useWorkspaces();
  const { workspaceId, projectId } = useActiveWorkspaceId(workspaces?.[0]?.id ?? 0);
  const { data: projects } = useProjects(workspaceId);
  const active = workspaces?.find((w) => w.id === workspaceId);

  return (
    <>
      <div className="flex h-12 shrink-0 items-center px-3">
        <Logo size="sm" href="/workspaces" />
      </div>

      <nav className="thin-scroll flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-2 pb-3">
        <WorkspaceSwitcher
          workspaces={workspaces ?? []}
          activeName={active?.name}
          activeId={workspaceId}
          onNavigate={onNavigate}
        />

        {workspaceId > 0 && (
          <Section label="프로젝트">
            {projects?.length === 0 ? (
              <p className="px-2 py-1 text-xs text-ink-faint">아직 없습니다</p>
            ) : (
              projects?.map((project) => (
                <NavItem
                  key={project.id}
                  href={`/projects/${project.id}`}
                  icon="folder"
                  label={project.name}
                  active={project.id === projectId}
                  onNavigate={onNavigate}
                />
              ))
            )}
          </Section>
        )}

        <Section label="계정">
          <NavItem
            href="/account"
            icon="user"
            label="계정 설정"
            active={pathname === '/account'}
            onNavigate={onNavigate}
          />
        </Section>
      </nav>

      <div className="flex shrink-0 items-center gap-1.5 border-t border-line px-2 py-2">
        <Link
          href="/account"
          onClick={onNavigate}
          className="flex min-w-0 flex-1 items-center gap-2 rounded-md px-1 py-1 transition-colors hover:bg-raised"
        >
          <Avatar name={user.name} size="sm" />
          <span className="min-w-0 truncate text-[13px] font-medium">{user.name}</span>
        </Link>
        {/* 목록이 사이드바 아래쪽에 있어 위로 펼친다 */}
        <NotificationBell panelClass="bottom-10 left-0" />
        <button
          onClick={() => logout.mutate()}
          disabled={logout.isPending}
          aria-label="로그아웃"
          title="로그아웃"
          className="flex size-8 items-center justify-center rounded-md text-ink-soft transition-colors hover:bg-raised hover:text-ink disabled:opacity-45"
        >
          <Icon name="logout" className="size-4.5" />
        </button>
      </div>
    </>
  );
}

function Section({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">{label}</p>
      {children}
    </div>
  );
}

function NavItem({
  href,
  icon,
  label,
  active,
  onNavigate,
}: {
  href: string;
  icon: IconName;
  label: string;
  active: boolean;
  onNavigate?: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={`flex items-center gap-2 rounded-md px-2 py-1.5 text-[13px] transition-colors ${
        active ? 'bg-accent-soft font-medium text-accent-ink' : 'text-ink-soft hover:bg-raised hover:text-ink'
      }`}
    >
      <Icon name={icon} className="size-4" />
      <span className="min-w-0 truncate">{label}</span>
    </Link>
  );
}

/** 워크스페이스가 여러 개인 사람은 하루에 몇 번씩 옮겨 다닌다. 목록 화면을 거치지 않게 한다. */
function WorkspaceSwitcher({
  workspaces,
  activeName,
  activeId,
  onNavigate,
}: {
  workspaces: { id: number; name: string }[];
  activeName?: string;
  activeId: number;
  onNavigate?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useDismiss(open, ref, () => setOpen(false));

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 rounded-md border border-line bg-surface px-2 py-1.5 text-left transition-colors hover:bg-raised"
      >
        <Icon name="layers" className="size-4 text-ink-faint" />
        <span className="min-w-0 flex-1 truncate text-[13px] font-medium">
          {activeName ?? '워크스페이스 선택'}
        </span>
        <Icon name="chevronDown" className="size-3.5 text-ink-faint" />
      </button>

      {open && (
        <div className="absolute left-0 right-0 top-9 z-30 overflow-hidden rounded-card border border-line bg-surface py-1 shadow-pop">
          <ul className="thin-scroll max-h-64 overflow-y-auto">
            {workspaces.map((workspace) => (
              <li key={workspace.id}>
                <Link
                  href={`/workspaces/${workspace.id}`}
                  onClick={() => {
                    setOpen(false);
                    onNavigate?.();
                  }}
                  className="flex items-center gap-2 px-2 py-1.5 text-[13px] transition-colors hover:bg-raised"
                >
                  <span className="min-w-0 flex-1 truncate">{workspace.name}</span>
                  {workspace.id === activeId && <Icon name="check" className="size-3.5 text-accent" />}
                </Link>
              </li>
            ))}
          </ul>
          <Link
            href="/workspaces"
            onClick={() => {
              setOpen(false);
              onNavigate?.();
            }}
            className="mt-1 flex items-center gap-2 border-t border-line px-2 pb-1 pt-2 text-[13px] text-ink-soft transition-colors hover:text-ink"
          >
            <Icon name="plus" className="size-3.5" />
            워크스페이스 만들기 · 전체 보기
          </Link>
        </div>
      )}
    </div>
  );
}
