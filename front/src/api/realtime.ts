import { Client, type StompConfig } from '@stomp/stompjs';
import { getAuthHeaders, getWebSocketUrl } from './platform';

/**
 * Creates a STOMP client for the backend's /ws endpoint. Credentials (MAX init
 * data, or the JWT outside MAX) go in the CONNECT frame — browsers can't set
 * headers on the WebSocket handshake — so the server can bind the session to
 * the current user, which per-user queues such as /user/queue/messages need.
 */
export function createStompClient(config: Omit<StompConfig, 'brokerURL'>): Client {
  return new Client({
    brokerURL: getWebSocketUrl(),
    ...config,
    connectHeaders: {
      ...getAuthHeaders(),
      ...config.connectHeaders,
    },
  });
}
