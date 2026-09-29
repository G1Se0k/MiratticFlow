import { Client, type IMessage } from '@stomp/stompjs';
import { api } from '@/lib/api/client';
import type { ChatMessage } from '@/lib/api/chat';
import type { UserResponse } from '@/lib/api/types';

/**
 * 운영: 같은 출처(wss://flow.mirattic.com/ws). 개발: 백엔드에 바로 붙는다 (Next 개발 서버는 WebSocket 을
 * 넘겨주지 않는다). 쿠키는 포트를 가리지 않으므로 127.0.0.1:3000 에서 받은 로그인 쿠키가 :8080 에도 붙는다.
 */
const WS_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://127.0.0.1:8080').replace(/^http/, 'ws') + '/ws';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

/**
 * 인증은 핸드셰이크에 브라우저가 붙이는 flow_at 쿠키로 한다 (서버의 StompAuthInterceptor 와 짝).
 * 스크립트는 토큰을 볼 수 없으므로 헤더로 보낼 것도 없다.
 * 쿠키의 access token 은 15분짜리이고 서버는 그 만료 시각에 연결을 끊는다(SocketExpiry). 그래서 연결(재연결 포함)
 * 전에 API 를 한 번 불러 필요하면 재발급받게 한다. 끊겨도 라이브러리가 다시 붙으면서 새 쿠키로 인증된다.
 *
 * 연결은 보고 있는 주제 하나당 하나씩 만든다. 주제를 옮기면 끊고 다시 연결한다.
 * 한 연결에 여러 구독을 얹는 편이 효율적이지만, 한 번에 한 주제만 보므로
 * 구독 수명을 따로 관리하지 않는 쪽이 단순하다.
 */
export function createChatClient({
  topicId,
  onMessage,
  onStateChange,
}: {
  topicId: number;
  onMessage: (message: ChatMessage) => void;
  onStateChange: (state: ConnectionState) => void;
}) {
  const client = new Client({
    brokerURL: WS_URL,
    beforeConnect: async () => {
      await api.get<UserResponse>('/api/users/me').catch(() => {});
    },
    reconnectDelay: 3000, // 끊기면 라이브러리가 알아서 다시 붙는다
    onConnect: () => {
      onStateChange('connected');
      client.subscribe(`/topic/thread/${topicId}`, (frame: IMessage) => {
        onMessage(JSON.parse(frame.body) as ChatMessage);
      });
    },
    onWebSocketClose: () => onStateChange('disconnected'),
    // 권한이 없거나 토큰이 틀리면 서버가 ERROR 프레임을 보내고 연결을 끊는다.
    onStompError: () => onStateChange('disconnected'),
  });

  onStateChange('connecting');
  client.activate();

  return {
    send: (content: string) => {
      client.publish({ destination: `/app/thread/${topicId}`, body: JSON.stringify({ content }) });
    },
    close: () => {
      void client.deactivate();
    },
  };
}
