import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';

import { AuthService, AuthState } from '../../core/services/auth';
import { ChatService } from '../../core/services/chat.service';
import { ClientApiService } from '../../core/services/client-api.service';
import { CustomerNotificationService } from '../../core/services/customer-notification.service';
import { ClientLayout } from './client-layout';

describe('ClientLayout', () => {
  beforeEach(async () => {
    localStorage.clear();
    const currentUser$ = new BehaviorSubject<AuthState>({
      isAuthenticated: false,
      username: '',
      fullName: '',
      avatarUrl: '',
      roles: [],
      permissions: [],
    });

    await TestBed.configureTestingModule({
      imports: [ClientLayout],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
          currentUser$,
            isLoggedIn: vi.fn(() => false),
            getCurrentUserId: vi.fn(() => null),
            getAccessToken: vi.fn(() => null),
            logout: vi.fn(),
            updateCurrentUser: vi.fn(),
          },
        },
        { provide: ClientApiService, useValue: { getProfile: vi.fn() } },
        {
          provide: CustomerNotificationService,
          useValue: {
            notifications$: new Subject(),
            connect: vi.fn(),
            disconnect: vi.fn(),
            getUnreadCount: vi.fn(() => of({ unreadCount: 0 })),
          },
        },
        {
          provide: ChatService,
          useValue: {
            connect: vi.fn(),
            disconnect: vi.fn(),
            sendCustomerMessage: vi.fn(() => false),
            getMyHistory: vi.fn(() => of([])),
            message$: new Subject(),
            connectionState$: of('idle'),
            connectionError$: of(''),
            isConnected: vi.fn(() => false),
          },
        },
      ],
    }).compileComponents();
  });

  it('renders the fixed Vietnamese locale and VND currency as non-interactive status', () => {
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const localeStatus = element.querySelector('.locale-status') as HTMLElement;

    expect(localeStatus).not.toBeNull();
    expect(localeStatus.getAttribute('role')).toBe('group');
    expect(localeStatus.getAttribute('aria-label')).toContain('Tiếng Việt');
    expect(localeStatus.getAttribute('aria-label')).toContain('Việt Nam đồng');
    expect(localeStatus.tabIndex).toBe(-1);
    expect(element.querySelector('.locale-button')).toBeNull();
  });
});
