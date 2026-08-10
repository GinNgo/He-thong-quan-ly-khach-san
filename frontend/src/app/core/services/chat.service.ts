import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, Message } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth';
import { ClientObservabilityService } from './client-observability.service';

export interface ChatMessage {
  id?: number;
  conversationId: number;
  hotelId: number;
  senderId: number;
  receiverId: number;
  content: string;
  timestamp?: string;
  isRead?: boolean;
}

export interface ChatConversation {
  conversationId: number;
  customerId: number;
  customerName: string;
  hotelId: number;
  hotelName: string;
  reservationId?: number;
  assignedAgentId?: number;
  channel: 'IN_APP' | 'TENANT_ADMIN';
  subject: string;
  status: 'OPEN' | 'ASSIGNED' | 'ESCALATED' | 'CLOSED';
  lastMessage: string;
  lastMessageAt?: string;
}

export type ChatMode = 'customer' | 'support';
export type ChatConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'error';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly observability = inject(ClientObservabilityService);
  private readonly logoutSubscription = this.authService.logout$.subscribe(() => this.disconnect());
  private readonly apiUrl = `${environment.apiUrl}/chat`;
  private stompClient: Client | null = null;
  private connectionMode: ChatMode | null = null;
  private connected = false;
  private connectionCorrelationId: string | null = null;

  private readonly messageSubject = new BehaviorSubject<ChatMessage | null>(null);
  private readonly connectionStateSubject = new BehaviorSubject<ChatConnectionState>('idle');
  private readonly connectionErrorSubject = new BehaviorSubject<string>('');

  readonly message$ = this.messageSubject.asObservable();
  readonly connectionState$ = this.connectionStateSubject.asObservable();
  readonly connectionError$ = this.connectionErrorSubject.asObservable();

  connect(mode: ChatMode): void {
    const token = this.authService.getAccessToken();
    if (!token) {
      this.setConnectionState('error', 'Phiên đăng nhập không còn hợp lệ.');
      return;
    }

    if (this.stompClient?.active && this.connectionMode === mode) {
      return;
    }

    this.disconnect();
    this.connectionMode = mode;
    this.connectionCorrelationId = this.observability.createCorrelationId('chat');
    this.setConnectionState('connecting', '');

    const client = new Client({
      webSocketFactory: () => new SockJS(`${this.apiOrigin()}/ws-chat`) as WebSocket,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        'X-Correlation-ID': this.connectionCorrelationId,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected = true;
        this.setConnectionState('connected', '');
        client.subscribe('/user/queue/messages', (message: Message) => this.publishIncoming(message));
        if (mode === 'support') {
          client.subscribe('/user/queue/support/messages', (message: Message) => this.publishIncoming(message));
        }
      },
      onStompError: (frame) => {
        this.connected = false;
        this.observability.recordStompFailure('chat', 'broker', this.connectionCorrelationId);
        this.setConnectionState('error', frame.headers['message'] || 'Kết nối chat bị từ chối.');
      },
      onWebSocketClose: () => {
        this.connected = false;
        if (client.active) {
          this.observability.recordStompFailure('chat', 'close', this.connectionCorrelationId);
          this.setConnectionState('reconnecting', 'Đang thử kết nối lại…');
        }
      },
      onWebSocketError: () => {
        this.connected = false;
        this.observability.recordStompFailure('chat', 'socket', this.connectionCorrelationId);
        this.setConnectionState('error', 'Không thể kết nối tới CSKH.');
      }
    });

    this.stompClient = client;
    client.activate();
  }

  disconnect(): void {
    const client = this.stompClient;
    this.connected = false;
    this.stompClient = null;
    this.connectionMode = null;
    this.connectionCorrelationId = null;
    this.setConnectionState('idle', '');
    if (client?.active) {
      void client.deactivate();
    }
  }

  sendCustomerMessage(content: string, hotelId?: number, reservationId?: number): boolean {
    return this.publish('/app/chat.support.send', { content, hotelId, reservationId });
  }

  sendTenantMessage(content: string, hotelId: number): boolean {
    return this.publish('/app/chat.tenant.send', { content, hotelId });
  }

  sendSupportReply(conversationId: number, content: string): boolean {
    return this.publish('/app/chat.support.reply', { conversationId, content });
  }

  getMyHistory(): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/me/history`);
  }

  getMyTenantSupportHistory(): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/tenant/history`);
  }

  getSupportConversations(): Observable<ChatConversation[]> {
    return this.http.get<ChatConversation[]>(`${this.apiUrl}/support/conversations`);
  }

  getSupportHistory(conversationId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/support/conversations/${conversationId}`);
  }

  assignConversation(conversationId: number): Observable<ChatConversation> {
    return this.http.post<ChatConversation>(`${this.apiUrl}/support/conversations/${conversationId}/assign`, {});
  }

  escalateConversation(conversationId: number): Observable<ChatConversation> {
    return this.http.post<ChatConversation>(`${this.apiUrl}/support/conversations/${conversationId}/escalate`, {});
  }

  isConnected(): boolean {
    return this.connected;
  }

  private publish(destination: string, body: object): boolean {
    if (!this.stompClient || !this.connected) {
      this.setConnectionState(this.connectionStateSubject.value, 'Chat đang offline. Hãy thử lại khi đã kết nối.');
      return false;
    }

    try {
      this.stompClient.publish({
        destination,
        body: JSON.stringify(body),
        headers: { 'X-Correlation-ID': this.observability.createCorrelationId('chat-message') },
      });
      return true;
    } catch {
      this.observability.recordStompFailure('chat', 'publish', this.connectionCorrelationId);
      this.setConnectionState('error', 'Không thể gửi tin nhắn. Hãy thử lại.');
      return false;
    }
  }

  private publishIncoming(message: Message): void {
    if (!message.body) return;
    try {
      this.messageSubject.next(JSON.parse(message.body) as ChatMessage);
    } catch {
      this.observability.recordStompFailure('chat', 'payload', this.connectionCorrelationId);
      this.setConnectionState('error', 'Tin nhắn nhận được không hợp lệ.');
    }
  }

  private setConnectionState(state: ChatConnectionState, error: string): void {
    this.connectionStateSubject.next(state);
    this.connectionErrorSubject.next(error);
  }

  private apiOrigin(): string {
    return environment.apiUrl.replace(/\/api\/?$/, '');
  }
}
