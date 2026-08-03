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
    connect: ReturnType<typeof vi.fn>;
    getInbox: ReturnType<typeof vi.fn>;
    markAsRead: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      notifications$: live$,
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
});
