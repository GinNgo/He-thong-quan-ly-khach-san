import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';

import { AuthService, AuthState } from '../../core/services/auth';
import { ChatService } from '../../core/services/chat.service';
import { ClientApiService } from '../../core/services/client-api.service';
import { CustomerNotificationService } from '../../core/services/customer-notification.service';
import { ClientLayout } from './client-layout';

describe('ClientLayout', () => {
  let currentUser$: BehaviorSubject<AuthState>;
  let getProfile: ReturnType<typeof vi.fn>;
  let updateCurrentUser: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    localStorage.clear();
    currentUser$ = new BehaviorSubject<AuthState>({
      isAuthenticated: false,
      username: '',
      fullName: '',
      avatarUrl: '',
      roles: [],
      permissions: [],
    });
    getProfile = vi.fn();
    updateCurrentUser = vi.fn((user: { username?: string; fullName?: string; avatarUrl?: string }) => {
      currentUser$.next({
        ...currentUser$.value,
        username: user.username ?? currentUser$.value.username,
        fullName: user.fullName ?? currentUser$.value.fullName,
        avatarUrl: user.avatarUrl ?? currentUser$.value.avatarUrl
      });
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
            updateCurrentUser,
          },
        },
        { provide: ClientApiService, useValue: { getProfile } },
        {
          provide: CustomerNotificationService,
          useValue: {
            notifications$: new Subject(),
            reconciliation$: new Subject(),
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

  afterEach(() => vi.restoreAllMocks());

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

  it('shows owner navigation after refreshed claim approval and targets the assigned property', async () => {
    const approvedOwner = {
      id: 42,
      username: 'owner',
      email: 'owner@example.com',
      fullName: 'Approved Owner',
      roles: ['PROPERTY_OWNER'],
      assignedProperties: [{ id: 17, name: 'Claimed Hotel' }],
      partnerRegistrationStatus: 'APPROVED' as const
    };
    getProfile.mockReturnValue(of(approvedOwner));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();

    currentUser$.next({
      isAuthenticated: true,
      username: 'owner',
      fullName: 'Approved Owner',
      avatarUrl: '',
      roles: ['PROPERTY_OWNER'],
      permissions: []
    });
    await fixture.whenStable();

    expect(getProfile).toHaveBeenCalledTimes(1);
    expect(updateCurrentUser).toHaveBeenCalled();
    expect(fixture.componentInstance.isPropertyOwner).toBe(true);
    expect(fixture.componentInstance.partnerLabel).toBe('Quản lý cơ sở');
    expect(fixture.componentInstance.managementQueryParams).toEqual({ propertyId: 17 });

    fixture.componentInstance.navigatePartner();
    expect(navigate).toHaveBeenCalledWith(['/management/dashboard'], {
      queryParams: { propertyId: 17 }
    });
    expect(getProfile).toHaveBeenCalledTimes(1);
  });

  it('keeps a pending claimant in registration status even when a stale assignment is present', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.componentInstance.isLoggedIn = true;
    fixture.componentInstance.userContext = {
      id: 42,
      username: 'claimant',
      email: 'claimant@example.com',
      roles: [],
      assignedProperties: [{ id: 17, name: 'Pending Import' }],
      partnerRegistrationStatus: 'PENDING'
    };

    expect(fixture.componentInstance.isPropertyOwner).toBe(false);
    expect(fixture.componentInstance.partnerLabel).toBe('Hồ sơ đang duyệt');
    fixture.componentInstance.navigatePartner();
    expect(navigate).toHaveBeenCalledWith(['/partner/registration-status']);
  });

  it('does not choose a replacement property when multiple assignments are returned', () => {
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.componentInstance.isLoggedIn = true;
    fixture.componentInstance.userContext = {
      id: 42,
      username: 'owner',
      email: 'owner@example.com',
      roles: ['PROPERTY_OWNER'],
      assignedProperties: [{ id: 17, name: 'First' }, { id: 18, name: 'Second' }]
    };

    expect(fixture.componentInstance.managementQueryParams).toBeNull();
  });
});
