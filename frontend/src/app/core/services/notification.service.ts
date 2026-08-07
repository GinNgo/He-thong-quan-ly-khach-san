import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, NgZone, OnDestroy, PLATFORM_ID } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, fromEvent, Observable, Subject, Subscription } from 'rxjs';
import SockJS from 'sockjs-client';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth';
import { ClientObservabilityService } from './client-observability.service';

export interface AppNotification {
  id: number;
  userId: number | null;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationHistoryPage {
  content: AppNotification[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  unreadCount: number;
  retentionDays: number;
}

export type NotificationConnectionState =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'offline'
  | 'error';

@Injectable({
  providedIn: 'root'
})
export class NotificationService implements OnDestroy {
  private readonly apiUrl = `${environment.apiUrl}/notifications`;
  private readonly notificationSubject = new Subject<AppNotification>();
  private readonly connectionStateSubject = new BehaviorSubject<NotificationConnectionState>('idle');
  private readonly connectionErrorSubject = new BehaviorSubject<string>('');
  private readonly lifecycleSubscriptions = new Subscription();
  private readonly browserPlatform: boolean;
  private stompClient: Client | null = null;
  private connectionCorrelationId: string | null = null;
  private reconnectRequested = false;

  readonly notifications$ = this.notificationSubject.asObservable();
  readonly connectionState$ = this.connectionStateSubject.asObservable();
  readonly connectionError$ = this.connectionErrorSubject.asObservable();

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private authService: AuthService,
    private observability: ClientObservabilityService,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this.browserPlatform = isPlatformBrowser(platformId);
    this.lifecycleSubscriptions.add(
      this.authService.logout$.subscribe(() => this.disconnect()),
    );

    if (this.browserPlatform) {
      this.lifecycleSubscriptions.add(
        fromEvent(window, 'offline').subscribe(() => {
          if (!this.reconnectRequested) return;
          this.setConnectionState(
            'offline',
            'Mất kết nối mạng. Thông báo sẽ kết nối lại khi có mạng.',
          );
        }),
      );
      this.lifecycleSubscriptions.add(
        fromEvent(window, 'online').subscribe(() => this.resumeAfterNetworkRecovery()),
      );
    }
  }

  connect(): void {
    this.reconnectRequested = true;
    if (!this.isOnline()) {
      this.setConnectionState(
        'offline',
        'Mất kết nối mạng. Thông báo sẽ kết nối lại khi có mạng.',
      );
      return;
    }

    const token = this.authService.getAccessToken();
    if (!token) {
      this.setConnectionState(
        'error',
        'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
      );
      return;
    }
    if (this.stompClient?.active) return;

    this.connectionCorrelationId = this.observability.createCorrelationId('notification');
    this.setConnectionState('connecting', 'Đang kết nối thông báo thời gian thực...');
    const client = this.createClient(token);
    this.stompClient = client;
    client.activate();
  }

  reconnect(): void {
    this.reconnectRequested = true;
    if (!this.isOnline()) {
      this.setConnectionState(
        'offline',
        'Mất kết nối mạng. Kiểm tra kết nối rồi thử lại.',
      );
      return;
    }

    const previousClient = this.stompClient;
    this.stompClient = null;
    if (previousClient?.active) {
      this.setConnectionState('connecting', 'Đang khởi tạo lại kết nối thông báo...');
      void previousClient.deactivate({ force: true }).finally(() => {
        if (this.reconnectRequested && !this.stompClient) this.connect();
      });
      return;
    }
    this.connect();
  }

  disconnect(): void {
    this.reconnectRequested = false;
    const client = this.stompClient;
    this.stompClient = null;
    this.connectionCorrelationId = null;
    this.setConnectionState('idle', '');
    if (client?.active) void client.deactivate({ force: true });
  }

  ngOnDestroy(): void {
    this.lifecycleSubscriptions.unsubscribe();
    this.disconnect();
  }

  getAdminNotifications(page = 0, size = 20): Observable<NotificationHistoryPage> {
    return this.http.get<NotificationHistoryPage>(this.apiUrl, {
      params: { page, size },
    });
  }

  markAsRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/read`, {});
  }

  private createClient(initialToken: string): Client {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl.replace('/api', '')}/ws`),
      connectHeaders: this.createConnectHeaders(initialToken),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      beforeConnect: async () => {
        const currentToken = this.authService.getAccessToken();
        if (!currentToken) {
          this.reconnectRequested = false;
          this.setConnectionState(
            'error',
            'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
          );
          await client.deactivate({ force: true });
          return;
        }
        this.connectionCorrelationId = this.observability.createCorrelationId('notification');
        client.connectHeaders = this.createConnectHeaders(currentToken);
      },
      onConnect: () => {
        if (this.stompClient !== client) return;
        this.setConnectionState('connected', '');
        const handleNotification = (message: IMessage) => this.handleNotification(message);
        client.subscribe('/topic/admin/notifications', handleNotification);
        client.subscribe('/user/queue/notifications', handleNotification);
      },
      onStompError: () => {
        this.observability.recordStompFailure('notification', 'broker', this.connectionCorrelationId);
        this.reconnectRequested = false;
        this.setConnectionState(
          'error',
          'Kết nối thông báo bị từ chối. Kiểm tra phiên đăng nhập rồi thử lại.',
        );
        void client.deactivate({ force: true });
      },
      onWebSocketError: () => {
        this.observability.recordStompFailure('notification', 'socket', this.connectionCorrelationId);
        this.setConnectionState(
          'reconnecting',
          'Không thể kết nối thông báo. Hệ thống đang thử lại...',
        );
      },
      onWebSocketClose: (event: CloseEvent) => {
        if (this.stompClient !== client || !this.reconnectRequested) return;
        if (!event.wasClean) {
          this.observability.recordStompFailure('notification', 'close', this.connectionCorrelationId);
        }
        if (!this.isOnline()) {
          this.setConnectionState(
            'offline',
            'Mất kết nối mạng. Thông báo sẽ kết nối lại khi có mạng.',
          );
        } else if (client.active) {
          this.setConnectionState(
            'reconnecting',
            'Mất kết nối thông báo. Hệ thống đang thử kết nối lại...',
          );
        }
      },
    });
    return client;
  }

  private handleNotification(message: IMessage): void {
    if (!message.body) return;
    try {
      const notification = JSON.parse(message.body) as AppNotification;
      this.ngZone.run(() => this.notificationSubject.next(notification));
    } catch {
      this.observability.recordStompFailure('notification', 'payload', this.connectionCorrelationId);
    }
  }

  private createConnectHeaders(token: string): Record<string, string> {
    return {
      Authorization: `Bearer ${token}`,
      'X-Correlation-ID': this.connectionCorrelationId
        ?? this.observability.createCorrelationId('notification'),
    };
  }

  private resumeAfterNetworkRecovery(): void {
    if (!this.reconnectRequested) return;
    this.setConnectionState(
      'reconnecting',
      'Đã có mạng. Đang kết nối lại thông báo...',
    );
    if (this.stompClient?.active) {
      this.stompClient.forceDisconnect();
    } else {
      this.connect();
    }
  }

  private isOnline(): boolean {
    return !this.browserPlatform || navigator.onLine;
  }

  private setConnectionState(state: NotificationConnectionState, message: string): void {
    this.ngZone.run(() => {
      this.connectionStateSubject.next(state);
      this.connectionErrorSubject.next(message);
    });
  }
}
