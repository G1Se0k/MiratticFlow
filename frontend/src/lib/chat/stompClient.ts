import { Client, type IMessage } from '@stomp/stompjs';
import { tokens } from '@/lib/auth/tokens';
import type { ChatMessage } from '@/lib/api/chat';

const WS_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080')
  .replace(/^http/, 'ws') + '/ws';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

/**
 * 브라우저 WebSocket 은 핸드셰이크에 헤더를 못 붙인다.
 * 그래서 토큰은 STOMP CONNECT 프레임의 connectHeaders 로 보낸다 (서버의 StompAuthInterceptor 와 짝).
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
    connectHeaders: { Authorization: `Bearer ${tokens.getAccess() ?? ''}` },
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
