import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, NgZone, OnDestroy, PLATFORM_ID } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Observable, Subject, Subscription } from 'rxjs';
import SockJS from 'sockjs-client';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

export const CUSTOMER_NOTIFICATION_DESTINATION = '/user/queue/notifications';

export interface CustomerNotification {
  id: number;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
  archivedAt?: string | null;
  deepLink: string;
}

export interface CustomerNotificationPage {
  content: CustomerNotification[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  unreadCount: number;
  archived?: boolean;
  retentionDays?: number;
}

export type NotificationEventClass =
  | 'ACCOUNT_SECURITY' | 'BOOKING' | 'PAYMENT' | 'REFUND' | 'INVOICE' | 'SUPPORT' | 'MARKETING';
export type NotificationChannel = 'IN_APP' | 'EMAIL';

export interface NotificationChannelPreference {
  channel: NotificationChannel;
  enabled: boolean;
  locked: boolean;
}

export interface NotificationPreferenceGroup {
  eventClass: NotificationEventClass;
  label: string;
  mandatory: boolean;
  channels: NotificationChannelPreference[];
}

@Injectable({ providedIn: 'root' })
export class CustomerNotificationService implements OnDestroy {
  private readonly apiUrl = `${environment.apiUrl}/customer/notifications`;
  private readonly notificationSubject = new Subject<CustomerNotification>();
  private readonly reconciliationSubject = new Subject<void>();
  private readonly subscriptions = new Subscription();
  private readonly browserPlatform: boolean;
  private stompClient: Client | null = null;

  readonly notifications$ = this.notificationSubject.asObservable();
  readonly reconciliation$ = this.reconciliationSubject.asObservable();
  private connectedOnce = false;

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    private readonly ngZone: NgZone,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this.browserPlatform = isPlatformBrowser(platformId);
    this.subscriptions.add(this.authService.logout$.subscribe(() => this.disconnect()));
  }

  getInbox(page = 0, size = 20, archived = false): Observable<CustomerNotificationPage> {
    return this.http.get<CustomerNotificationPage>(this.apiUrl, { params: { page, size, archived } });
  }

  getUnreadCount(): Observable<{ unreadCount: number }> {
    return this.http.get<{ unreadCount: number }>(`${this.apiUrl}/unread-count`);
  }

  markAsRead(notificationId: number): Observable<CustomerNotification> {
    return this.http.post<CustomerNotification>(`${this.apiUrl}/${notificationId}/read`, {});
  }

  archive(notificationId: number): Observable<CustomerNotification> {
    return this.http.post<CustomerNotification>(`${this.apiUrl}/${notificationId}/archive`, {});
  }

  restore(notificationId: number): Observable<CustomerNotification> {
    return this.http.put<CustomerNotification>(`${this.apiUrl}/${notificationId}/restore`, {});
  }

  getPreferences(): Observable<NotificationPreferenceGroup[]> {
    return this.http.get<NotificationPreferenceGroup[]>(`${this.apiUrl}/preferences`);
  }

  updatePreferences(preferences: Array<{
    eventClass: NotificationEventClass;
    channel: NotificationChannel;
    enabled: boolean;
  }>): Observable<NotificationPreferenceGroup[]> {
    return this.http.put<NotificationPreferenceGroup[]>(
      `${this.apiUrl}/preferences`, { preferences });
  }

  connect(): void {
    if (!this.browserPlatform || this.stompClient?.active) return;
    const token = this.authService.getAccessToken();
    if (!token) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl.replace('/api', '')}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      beforeConnect: async () => {
        const currentToken = this.authService.getAccessToken();
        if (!currentToken) {
          await client.deactivate({ force: true });
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${currentToken}` };
      },
      onConnect: () => {
        client.subscribe(CUSTOMER_NOTIFICATION_DESTINATION, message => this.handleMessage(message));
        if (this.connectedOnce) {
          this.ngZone.run(() => this.reconciliationSubject.next());
        }
        this.connectedOnce = true;
      },
    });
    this.stompClient = client;
    client.activate();
  }

  disconnect(): void {
    const client = this.stompClient;
    this.stompClient = null;
    if (client?.active) void client.deactivate({ force: true });
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.disconnect();
  }

  private handleMessage(message: IMessage): void {
    if (!message.body) return;
    try {
      const parsed = JSON.parse(message.body) as CustomerNotification;
      const notification = {
        ...parsed,
        deepLink: parsed.deepLink || this.deepLinkFor(parsed.type),
      };
      this.ngZone.run(() => this.notificationSubject.next(notification));
    } catch {
      // Malformed broker payloads are ignored and never reach the customer inbox.
    }
  }

  private deepLinkFor(type: string): string {
    switch ((type || '').trim().toUpperCase()) {
      case 'BOOKING':
      case 'RESERVATION':
      case 'PAYMENT':
        return '/booking-history';
      case 'INVOICE':
        return '/my-invoices';
      case 'REFUND':
        return '/refunds';
      case 'CHAT':
      case 'SUPPORT':
        return '/?support=open';
      default:
        return '/notifications';
    }
  }
}
