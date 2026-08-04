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
  conversationId?: number;
  hotelId?: number;
  clientMessageId?: string;
  senderId: number;
  receiverId: number;
  content: string;
  timestamp?: string;
  isRead?: boolean;
  deliveryStatus?: 'PERSISTED' | 'DELIVERED' | 'READ';
  deliveredAt?: string;
  readAt?: string;
}

export interface ChatConversation {
  conversationId: number;
  customerId: number;
  customerName: string;
  subject: string;
  hotelId?: number;
  hotelName?: string;
  reservationId?: number;
  assignedAgentId?: number;
  assignedAgentName?: string;
  status?: 'OPEN' | 'ASSIGNED' | 'ESCALATED' | 'CLOSED';
  version?: number;
  slaDeadlineAt?: string;
  slaState?: 'BREACHED' | 'AT_RISK' | 'ON_TRACK' | 'NO_PENDING_RESPONSE';
  createdAt?: string;
  lastActivityAt?: string;
  assignedAt?: string;
  escalatedAt?: string;
  closedAt?: string;
  firstResponseAt?: string;
  lastCustomerMessageAt?: string;
  lastSupportReplyAt?: string;
  closedReason?: string;
  reopenedAt?: string;
  reopenReason?: string;
  lastMessage: string;
  lastMessageAt?: string;
}

export interface ChatPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  retentionDays: number;
}

export interface SupportQueueFilters {
  status?: 'ALL' | 'OPEN' | 'ASSIGNED' | 'ESCALATED' | 'CLOSED';
  assignment?: 'ALL' | 'UNASSIGNED' | 'MINE';
  sla?: 'ALL' | 'BREACHED' | 'AT_RISK' | 'ON_TRACK' | 'NO_PENDING_RESPONSE';
  hotelId?: number;
  query?: string;
}

export interface SupportAttachment {
  id: number;
  conversationId: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256: string;
  uploadedByUserId: number;
  uploadedAt: string;
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

  sendCustomerMessage(content: string, conversationId?: number, clientMessageId?: string): boolean {
    return this.publish('/app/chat.support.send', { content, conversationId, clientMessageId });
  }

  sendSupportReply(conversationId: number, content: string, clientMessageId?: string): boolean {
    return this.publish('/app/chat.support.reply', { conversationId, content, clientMessageId });
  }

  getMyHistory(): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/me/history`);
  }

  getMyConversations(page = 0, size = 20): Observable<ChatPage<ChatConversation>> {
    return this.http.get<ChatPage<ChatConversation>>(`${this.apiUrl}/me/conversations`, {
      params: { page, size },
    });
  }

  createMyConversation(subject: string, hotelId?: number, reservationId?: number): Observable<ChatConversation> {
    return this.http.post<ChatConversation>(`${this.apiUrl}/me/conversations`, {
      subject,
      ...(hotelId ? { hotelId } : {}),
      ...(reservationId ? { reservationId } : {}),
    });
  }

  getMyConversationMessages(conversationId: number, page = 0, size = 50): Observable<ChatPage<ChatMessage>> {
    return this.http.get<ChatPage<ChatMessage>>(
      `${this.apiUrl}/me/conversations/${conversationId}/messages`, { params: { page, size } });
  }

  sendMyConversationMessage(
    conversationId: number, content: string, clientMessageId?: string
  ): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(
      `${this.apiUrl}/me/conversations/${conversationId}/messages`, {
        content,
        ...(clientMessageId ? { clientMessageId } : {}),
      });
  }

  getMyAttachments(conversationId: number): Observable<SupportAttachment[]> {
    return this.http.get<SupportAttachment[]>(
      `${this.apiUrl}/me/conversations/${conversationId}/attachments`);
  }

  uploadMyAttachment(conversationId: number, file: File): Observable<SupportAttachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<SupportAttachment>(
      `${this.apiUrl}/me/conversations/${conversationId}/attachments`, form);
  }

  getSupportConversations(filters: SupportQueueFilters = {}): Observable<ChatConversation[]> {
    const params: Record<string, string | number> = {};
    if (filters.status && filters.status !== 'ALL') params['status'] = filters.status;
    if (filters.assignment && filters.assignment !== 'ALL') params['assignment'] = filters.assignment;
    if (filters.sla && filters.sla !== 'ALL') params['sla'] = filters.sla;
    if (filters.hotelId) params['hotelId'] = filters.hotelId;
    if (filters.query?.trim()) params['query'] = filters.query.trim();
    return this.http.get<ChatConversation[]>(`${this.apiUrl}/support/conversations`, { params });
  }

  getSupportHistory(conversationId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/support/conversations/${conversationId}`);
  }

  getSupportConversationMessages(conversationId: number, page = 0, size = 50): Observable<ChatPage<ChatMessage>> {
    return this.http.get<ChatPage<ChatMessage>>(
      `${this.apiUrl}/support/conversations/${conversationId}/messages`, { params: { page, size } });
  }

  sendSupportConversationMessage(
    conversationId: number, content: string, expectedVersion?: number, clientMessageId?: string
  ): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(
      `${this.apiUrl}/support/conversations/${conversationId}/messages`, {
        conversationId,
        content,
        ...(expectedVersion === undefined ? {} : { expectedVersion }),
        ...(clientMessageId ? { clientMessageId } : {}),
      });
  }

  acknowledgeMessage(
    messageId: number, state: 'DELIVERED' | 'READ'
  ): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiUrl}/messages/${messageId}/state`, null, {
      params: { state },
    });
  }

  createClientMessageId(): string {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    return `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  claimSupportConversation(conversationId: number, expectedVersion?: number): Observable<ChatConversation> {
    return this.mutateConversation(conversationId, 'assign', expectedVersion);
  }

  unassignSupportConversation(conversationId: number, expectedVersion?: number): Observable<ChatConversation> {
    return this.mutateConversation(conversationId, 'unassign', expectedVersion);
  }

  escalateSupportConversation(conversationId: number, expectedVersion?: number): Observable<ChatConversation> {
    return this.mutateConversation(conversationId, 'escalate', expectedVersion);
  }

  closeSupportConversation(
    conversationId: number, reason: string, expectedVersion?: number
  ): Observable<ChatConversation> {
    return this.mutateConversation(conversationId, 'close', expectedVersion, reason);
  }

  reopenSupportConversation(
    conversationId: number, reason: string, expectedVersion?: number
  ): Observable<ChatConversation> {
    return this.mutateConversation(conversationId, 'reopen', expectedVersion, reason);
  }

  getSupportAttachments(conversationId: number): Observable<SupportAttachment[]> {
    return this.http.get<SupportAttachment[]>(
      `${this.apiUrl}/support/conversations/${conversationId}/attachments`);
  }

  uploadSupportAttachment(conversationId: number, file: File): Observable<SupportAttachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<SupportAttachment>(
      `${this.apiUrl}/support/conversations/${conversationId}/attachments`, form);
  }

  downloadAttachment(attachmentId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/attachments/${attachmentId}`, { responseType: 'blob' });
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
      this.setConnectionState('error', 'Không thể gửi tin nhắn. Hãy thử lại.');
      return false;
    }
  }

  private mutateConversation(
    conversationId: number,
    action: 'assign' | 'unassign' | 'escalate' | 'close' | 'reopen',
    expectedVersion?: number,
    reason?: string,
  ): Observable<ChatConversation> {
    const params: Record<string, number> = {};
    if (expectedVersion !== undefined) params['expectedVersion'] = expectedVersion;
    return this.http.post<ChatConversation>(
      `${this.apiUrl}/support/conversations/${conversationId}/${action}`,
      reason === undefined ? null : { reason }, { params });
  }

  private publishIncoming(message: Message): void {
    if (!message.body) return;
    try {
      this.messageSubject.next(JSON.parse(message.body) as ChatMessage);
    } catch {
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
