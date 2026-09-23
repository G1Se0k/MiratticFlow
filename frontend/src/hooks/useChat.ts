'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { chatApi, type ChatMessage } from '@/lib/api/chat';
import { createChatClient, type ConnectionState } from '@/lib/chat/stompClient';

export const chatKeys = {
  projectChat: (projectId: number) => ['projects', projectId, 'chat'] as const,
  topics: (issueId: number) => ['issues', issueId, 'topics'] as const,
  messages: (topicId: number) => ['topics', topicId, 'messages'] as const,
};

/** 프로젝트 채팅. 프로젝트를 만들 때 함께 생기므로 항상 하나 있다. */
export const useProjectChat = (projectId: number) =>
  useQuery({
    queryKey: chatKeys.projectChat(projectId),
    queryFn: () => chatApi.projectChat(projectId),
    enabled: projectId > 0,
  });

export const useTopics = (issueId: number) =>
  useQuery({
    queryKey: chatKeys.topics(issueId),
    queryFn: () => chatApi.topics(issueId),
    enabled: issueId > 0,
  });

export function useTopicMutations(issueId: number) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: chatKeys.topics(issueId) });

  return {
    create: useMutation({
      mutationFn: (body: { name: string; description?: string }) => chatApi.createTopic(issueId, body),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: ({ id, ...body }: { id: number; name: string; description?: string }) =>
        chatApi.updateTopic(id, body),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: number) => chatApi.removeTopic(id), onSuccess: refresh }),
  };
}

/**
 * 과거 메시지는 REST 로 한 번 받고, 이후 새 메시지는 WebSocket 으로 이어붙인다.
 * 둘을 합친 배열을 화면에 돌려준다.
 */
export function useChat(topicId: number) {
  const [live, setLive] = useState<ChatMessage[]>([]);
  const [state, setState] = useState<ConnectionState>('connecting');
  const clientRef = useRef<ReturnType<typeof createChatClient> | null>(null);

  const { data: history = [], isPending } = useQuery({
    queryKey: chatKeys.messages(topicId),
    queryFn: () => chatApi.messages(topicId),
    enabled: topicId > 0,
    // staleTime 을 두지 않는다. 주제를 열 때마다 최신 목록을 다시 받아야 한다.
    // WebSocket 으로 받은 메시지는 화면을 벗어나면 사라지므로, 돌아왔을 때
    // 캐시된 옛 목록을 그대로 쓰면 그 사이 대화가 통째로 비어 보인다.
  });

  useEffect(() => {
    if (topicId <= 0) return;
    setLive([]); // 주제를 옮기면 이전 주제의 실시간 메시지를 버린다

    const client = createChatClient({
      topicId,
      onMessage: (message) =>
        // 재연결 직후 같은 메시지가 두 번 올 수 있어 id 로 거른다
        setLive((prev) => (prev.some((m) => m.id === message.id) ? prev : [...prev, message])),
      onStateChange: setState,
    });
    clientRef.current = client;

    return () => {
      client.close();
      clientRef.current = null;
    };
  }, [topicId]);

  const send = useCallback((content: string) => clientRef.current?.send(content), []);

  // 과거 조회 뒤에 도착한 것만 남긴다. REST 응답과 WebSocket 수신이 겹칠 수 있다.
  const lastHistoryId = history.at(-1)?.id ?? 0;
  const messages = [...history, ...live.filter((m) => m.id > lastHistoryId)];

  return { messages, isPending, state, send };
}
