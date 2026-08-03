import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { AuthService } from './auth';
import {
  CUSTOMER_NOTIFICATION_DESTINATION,
  CustomerNotificationService,
} from './customer-notification.service';

describe('CustomerNotificationService', () => {
  let service: CustomerNotificationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            logout$: new Subject<void>(),
            getAccessToken: vi.fn(() => 'customer-token'),
          },
        },
      ],
    });
    service = TestBed.inject(CustomerNotificationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the authenticated personal STOMP destination', () => {
    expect(CUSTOMER_NOTIFICATION_DESTINATION).toBe('/user/queue/notifications');
  });

  it('loads the own inbox and unread count from customer-only endpoints', () => {
    service.getInbox(1, 10, true).subscribe();
    const inbox = http.expectOne(request => request.url.endsWith('/customer/notifications'));
    expect(inbox.request.params.get('page')).toBe('1');
    expect(inbox.request.params.get('size')).toBe('10');
    expect(inbox.request.params.get('archived')).toBe('true');
    inbox.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 10, first: false, last: true, unreadCount: 0 });

    service.getUnreadCount().subscribe(result => expect(result.unreadCount).toBe(3));
    http.expectOne(request => request.url.endsWith('/customer/notifications/unread-count'))
      .flush({ unreadCount: 3 });
  });

  it('marks a notification through the customer ownership endpoint', () => {
    service.markAsRead(42).subscribe(result => expect(result.isRead).toBe(true));
    const request = http.expectOne(req => req.url.endsWith('/customer/notifications/42/read'));
    expect(request.request.method).toBe('POST');
    request.flush({
      id: 42,
      type: 'REFUND',
      title: 'Refund updated',
      message: 'Done',
      isRead: true,
      createdAt: '2026-08-04T12:00:00',
      deepLink: '/refunds',
    });
  });

  it('archives, restores and updates own-channel preferences', () => {
    service.archive(42).subscribe();
    const archive = http.expectOne(req => req.url.endsWith('/customer/notifications/42/archive'));
    expect(archive.request.method).toBe('POST');
    archive.flush({});

    service.restore(42).subscribe();
    const restore = http.expectOne(req => req.url.endsWith('/customer/notifications/42/restore'));
    expect(restore.request.method).toBe('PUT');
    restore.flush({});

    service.updatePreferences([{ eventClass: 'MARKETING', channel: 'EMAIL', enabled: true }]).subscribe();
    const preferences = http.expectOne(req => req.url.endsWith('/customer/notifications/preferences'));
    expect(preferences.request.method).toBe('PUT');
    expect(preferences.request.body.preferences[0].eventClass).toBe('MARKETING');
    preferences.flush([]);
  });
});
