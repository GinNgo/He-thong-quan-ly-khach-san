import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { Client, IFrame, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

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

  public notifications$ = this.notificationSubject.asObservable();

  constructor(private http: HttpClient, private ngZone: NgZone) {
    this.stompClient = new Client({
      // Dùng SockJS để hỗ trợ tốt hơn
      webSocketFactory: () => new SockJS(`${environment.apiUrl.replace('/api', '')}/ws`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('STOMP connected');
        this.stompClient.subscribe('/topic/notifications', (message: IMessage) => {
          if (message.body) {
            const notif = JSON.parse(message.body) as AppNotification;
            this.ngZone.run(() => {
              this.notificationSubject.next(notif);
            });
          }
        });
      },
      onStompError: (frame: IFrame) => {
        console.error('STOMP Error:', frame.headers['message']);
      }
    });
  }

  // Kết nối khi Admin đăng nhập hoặc ở AdminLayout
  connect() {
    if (!this.stompClient.active) {
      this.stompClient.activate();
    }
  }

  // Ngắt kết nối khi logout
  disconnect() {
    if (this.stompClient.active) {
      this.stompClient.deactivate();
    }
  }

  getAdminNotifications(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.apiUrl);
  }

  markAsRead(id: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${id}/read`, {});
  }
}
