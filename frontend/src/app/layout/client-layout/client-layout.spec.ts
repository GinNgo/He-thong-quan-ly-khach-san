import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';

import { AuthService, AuthState } from '../../core/services/auth';
import { ChatService } from '../../core/services/chat.service';
<<<<<<< HEAD
import { ClientApiService } from '../../core/services/client-api.service';
import { CustomerNotificationService } from '../../core/services/customer-notification.service';
=======
import { ClientApiService, UserContext } from '../../core/services/client-api.service';
>>>>>>> codex/ui-functional-audit-polish
import { ClientLayout } from './client-layout';

describe('ClientLayout', () => {
  let currentUser$: BehaviorSubject<AuthState>;
<<<<<<< HEAD
  let getProfile: ReturnType<typeof vi.fn>;
  let updateCurrentUser: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    localStorage.clear();
=======
  const getProfile = vi.fn();

  beforeEach(async () => {
    localStorage.clear();
    getProfile.mockReset();
>>>>>>> codex/ui-functional-audit-polish
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
        provideTranslateService(),
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
<<<<<<< HEAD
=======
        { provide: ClientApiService, useValue: { getProfile } },
>>>>>>> codex/ui-functional-audit-polish
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

<<<<<<< HEAD
  afterEach(() => vi.restoreAllMocks());

  it('renders the fixed Vietnamese locale and VND currency as non-interactive status', () => {
=======
  it('renders an accessible VI/EN control while keeping VND fixed', () => {
>>>>>>> codex/ui-functional-audit-polish
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const localeButton = element.querySelector('.locale-button') as HTMLButtonElement;

    expect(localeButton).not.toBeNull();
    expect(localeButton.textContent).toContain('VI');
    expect(localeButton.textContent).toContain('VND');

    localeButton.click();
    fixture.detectChanges();

    expect(localeButton.textContent).toContain('EN');
    expect(localStorage.getItem('luxestay.locale')).toBe('en');
  });

  it('renders grouped footer navigation and support-safe content structure', () => {
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.public-footer')).not.toBeNull();
    expect(element.querySelectorAll('.footer-column')).toHaveLength(3);
    expect(element.querySelector('.footer-column a[href="/support"]')).not.toBeNull();
    expect(element.querySelector('.footer-column a[href="/privacy"]')).not.toBeNull();
    expect(element.querySelector('.footer-column a[href="/terms"]')).not.toBeNull();
    expect(element.querySelector('a[href^="tel:"]')).not.toBeNull();
  });

  it('keeps partner acquisition out of the primary header actions', () => {
    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.header-actions .partner-button')).toBeNull();
    expect(element.querySelector('.mobile-nav .mobile-partner-button')).toBeNull();
    expect(element.querySelector('.footer-column button')).not.toBeNull();
  });

  it('keeps rewards and account navigation available when an authenticated menu opens', () => {
    const context: UserContext = {
      id: 42,
      username: 'customer@example.test',
      email: 'customer@example.test',
      fullName: 'Nguyen Van An',
      points: 1250,
      roles: ['CUSTOMER'],
      partnerRegistrationStatus: 'NONE',
      pendingBookingCount: 2,
    };
    getProfile.mockReturnValue(of(context));

    const fixture = TestBed.createComponent(ClientLayout);
    fixture.detectChanges();
    currentUser$.next({
      isAuthenticated: true,
      username: context.username,
      fullName: context.fullName || context.username,
      avatarUrl: '',
      roles: ['CUSTOMER'],
      permissions: [],
    });
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('.account-trigger') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const menu = element.querySelector('.account-menu');
    expect(element.querySelector('.public-header')?.classList.contains('account-menu-open')).toBe(true);
    expect(menu).not.toBeNull();
    expect(menu?.querySelector('.account-rewards-value strong')?.textContent).toMatch(/1[,.]250/);
    expect(menu?.querySelectorAll('nav [role="menuitem"]').length).toBeGreaterThanOrEqual(7);
    expect(menu?.querySelector('.menu-close')?.getAttribute('aria-label')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(element.querySelector('.account-menu')).toBeNull();
    expect(element.querySelector('.public-header')?.classList.contains('account-menu-open')).toBe(false);
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
