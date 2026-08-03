import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
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

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private apiUrl = `${environment.apiUrl}/notifications`;
  private stompClient: Client;
  private notificationSubject = new Subject<AppNotification>();
  private connectionCorrelationId: string | null = null;

  public notifications$ = this.notificationSubject.asObservable();

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private authService: AuthService,
    private observability: ClientObservabilityService
  ) {
    this.authService.logout$.subscribe(() => this.disconnect());
    this.stompClient = new Client({
      // Dùng SockJS để hỗ trợ tốt hơn
      webSocketFactory: () => new SockJS(`${environment.apiUrl.replace('/api', '')}/ws`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.stompClient.subscribe('/topic/notifications', (message: IMessage) => {
          if (message.body) {
            const notif = JSON.parse(message.body) as AppNotification;
            this.ngZone.run(() => {
              this.notificationSubject.next(notif);
            });
          }
        });
      },
      onStompError: () => {
        this.observability.recordStompFailure('notification', 'broker', this.connectionCorrelationId);
      },
      onWebSocketError: () => {
        this.observability.recordStompFailure('notification', 'socket', this.connectionCorrelationId);
      },
      onWebSocketClose: (event: CloseEvent) => {
        if (!event.wasClean) {
          this.observability.recordStompFailure('notification', 'close', this.connectionCorrelationId);
        }
      }
    });
  }

  // Kết nối khi Admin đăng nhập hoặc ở AdminLayout
  connect() {
    this.connectionCorrelationId = this.observability.createCorrelationId('notification');
    this.stompClient.connectHeaders = { 'X-Correlation-ID': this.connectionCorrelationId };
    if (!this.stompClient.active) {
      this.stompClient.activate();
    }
  }

  // Ngắt kết nối khi logout
  disconnect() {
    if (this.stompClient.active) {
      this.stompClient.deactivate();
    }
    this.connectionCorrelationId = null;
  }

  getAdminNotifications(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.apiUrl);
  }

  markAsRead(id: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${id}/read`, {});
  }
}
