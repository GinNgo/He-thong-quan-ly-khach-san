import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import {
  CustomerNotification,
  CustomerNotificationService,
} from '../../../core/services/customer-notification.service';
import { CustomerNotificationsComponent } from './customer-notifications.component';

describe('CustomerNotificationsComponent', () => {
  const live$ = new Subject<CustomerNotification>();
  const notification: CustomerNotification = {
    id: 10,
    type: 'BOOKING',
    title: 'Booking confirmed',
    message: 'Your stay is ready.',
    isRead: false,
    createdAt: '2026-08-04T10:00:00',
    deepLink: '/booking-history',
  };
  let api: {
    notifications$: Subject<CustomerNotification>;
    reconciliation$: Subject<void>;
    connect: ReturnType<typeof vi.fn>;
    getInbox: ReturnType<typeof vi.fn>;
    markAsRead: ReturnType<typeof vi.fn>;
    archive: ReturnType<typeof vi.fn>;
    restore: ReturnType<typeof vi.fn>;
    getPreferences: ReturnType<typeof vi.fn>;
    updatePreferences: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      notifications$: live$,
      reconciliation$: new Subject<void>(),
      connect: vi.fn(),
      getInbox: vi.fn(() => of({
        content: [notification],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
        first: true,
        last: true,
        unreadCount: 1,
      })),
      markAsRead: vi.fn(() => of({ ...notification, isRead: true })),
      archive: vi.fn(() => of({ ...notification, archivedAt: '2026-08-04T11:00:00' })),
      restore: vi.fn(() => of({ ...notification, archivedAt: null })),
      getPreferences: vi.fn(() => of([{
        eventClass: 'BOOKING',
        label: 'Dat phong',
        mandatory: true,
        channels: [
          { channel: 'IN_APP', enabled: true, locked: true },
          { channel: 'EMAIL', enabled: true, locked: false },
        ],
      }, {
        eventClass: 'MARKETING',
        label: 'Uu dai va tin tuc',
        mandatory: false,
        channels: [
          { channel: 'IN_APP', enabled: false, locked: false },
          { channel: 'EMAIL', enabled: false, locked: false },
        ],
      }])),
      updatePreferences: vi.fn(preferences => of(preferences)),
    };
    await TestBed.configureTestingModule({
      imports: [CustomerNotificationsComponent],
      providers: [
        provideRouter([]),
        { provide: CustomerNotificationService, useValue: api },
      ],
    }).compileComponents();
  });

  it('renders own unread notifications and actionable deep links', () => {
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(api.connect).toHaveBeenCalled();
    expect(element.textContent).toContain('Booking confirmed');
    expect(element.textContent).toContain('1 chua doc');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/booking-history');
  });

  it('updates unread state after the customer marks a row read', () => {
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();

    fixture.componentInstance.markAsRead(notification);
    fixture.detectChanges();

    expect(api.markAsRead).toHaveBeenCalledWith(10);
    expect(fixture.componentInstance.unreadCount).toBe(0);
    expect(fixture.componentInstance.notifications[0].isRead).toBe(true);
  });

  it('shows an accessible retry state when inbox loading fails', () => {
    api.getInbox.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Thu lai');
  });

  it('reconciles persisted rows and unread count after a realtime reconnect', () => {
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();
    const reconciled = {
      ...notification,
      id: 11,
      title: 'Refund completed',
      type: 'REFUND',
      deepLink: '/refunds',
    };
    api.getInbox.mockReturnValue(of({
      content: [reconciled, notification],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      unreadCount: 2,
    }));

    api.reconciliation$.next();
    fixture.detectChanges();

    expect(fixture.componentInstance.notifications.map(item => item.id)).toEqual([11, 10]);
    expect(fixture.componentInstance.unreadCount).toBe(2);
    expect(api.getInbox).toHaveBeenLastCalledWith(0, 20, false);
  });

  it('loads accessible settings and keeps mandatory in-app events locked', () => {
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();

    fixture.componentInstance.toggleSettings();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(api.getPreferences).toHaveBeenCalled();
    expect(element.querySelector('fieldset')).not.toBeNull();
    const locked = element.querySelector('input[disabled]') as HTMLInputElement;
    expect(locked.checked).toBe(true);
    expect(element.textContent).toContain('Luon bat');
  });

  it('switches to archived history and restores an owned row', () => {
    api.getInbox.mockReturnValue(of({
      content: [{ ...notification, archivedAt: '2026-08-04T11:00:00' }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      unreadCount: 0,
      archived: true,
      retentionDays: 365,
    }));
    const fixture = TestBed.createComponent(CustomerNotificationsComponent);
    fixture.detectChanges();

    fixture.componentInstance.showHistory(true);
    fixture.detectChanges();
    fixture.componentInstance.restore(notification);

    expect(api.getInbox).toHaveBeenCalledWith(0, 20, true);
    expect(api.restore).toHaveBeenCalledWith(10);
  });
});
