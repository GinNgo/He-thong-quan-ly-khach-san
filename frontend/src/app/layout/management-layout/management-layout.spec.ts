import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService, AuthState } from '../../core/services/auth';
import { ManagementApiService, ManagementContext } from '../../core/services/management-api.service';
import { PermissionService } from '../../core/services/permission.service';
import { ManagementLayout } from './management-layout';

describe('ManagementLayout', () => {
  it('renders the property selector after context loads', async () => {
    const context$ = new Subject<ManagementContext>();
    const user$ = new BehaviorSubject<AuthState>({
      isAuthenticated: true,
      username: 'manager1',
      fullName: 'Manager One',
      avatarUrl: '',
      roles: ['HOTEL_MANAGER'],
      permissions: [],
    });

    await TestBed.configureTestingModule({
      imports: [ManagementLayout],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser$: user$, logout: () => undefined } },
        { provide: ManagementApiService, useValue: { context: () => context$ } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementLayout);
    fixture.detectChanges();

    context$.next({
      properties: [{ id: 1, code: 'HOTEL-1', nameVi: 'LuxeStay Hà Nội', propertyType: 'HOTEL', address: 'Hà Nội', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', isDemo: false }],
      activePropertyId: 1,
      planCode: 'STANDARD',
      subscriptionStatus: 'ACTIVE',
      lifetime: false,
      limits: {},
      usage: {},
      upgradeRequired: false,
    });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('Đang tải...');
    expect(element.querySelector('#active-property')).not.toBeNull();
    expect(element.textContent).toContain('LuxeStay Hà Nội');
  });

  it('removes the closed mobile sidebar from keyboard navigation', async () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = vi.fn(() => ({
      matches: true,
      media: '(max-width: 991px)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    })) as typeof window.matchMedia;
    await TestBed.configureTestingModule({
      imports: [ManagementLayout],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            currentUser$: new BehaviorSubject<AuthState>({
              isAuthenticated: true,
              username: 'owner',
              fullName: 'Owner',
              avatarUrl: '',
              roles: ['PROPERTY_OWNER'],
              permissions: [],
            }),
            logout: () => undefined,
          },
        },
        { provide: ManagementApiService, useValue: { context: () => new Subject<ManagementContext>() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementLayout);
    fixture.detectChanges();

    const sidebar = fixture.nativeElement.querySelector('#management-navigation') as HTMLElement;
    expect(sidebar.getAttribute('aria-hidden')).toBe('true');
    expect(sidebar.inert).toBe(true);

    fixture.componentInstance.toggleSidebar();
    fixture.detectChanges();
    expect(fixture.componentInstance.sidebarExpanded).toBe(true);
    window.matchMedia = originalMatchMedia;
  });

  it('shows the payment configuration menu only with its dedicated view permission', async () => {
    let allowed = false;
    await TestBed.configureTestingModule({
      imports: [ManagementLayout],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            currentUser$: new BehaviorSubject<AuthState>({
              isAuthenticated: true,
              username: 'owner',
              fullName: 'Owner',
              avatarUrl: '',
              roles: ['PROPERTY_OWNER'],
              permissions: [],
            }),
            logout: () => undefined,
          },
        },
        { provide: ManagementApiService, useValue: { context: () => new Subject<ManagementContext>() } },
        { provide: PermissionService, useValue: { hasPermission: vi.fn(() => allowed) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementLayout);
    fixture.detectChanges();
    const hasPaymentLink = () => Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>)
      .some((link) => link.textContent?.includes('Cấu hình thanh toán'));
    expect(hasPaymentLink()).toBe(false);

    allowed = true;
    fixture.destroy();
    const allowedFixture = TestBed.createComponent(ManagementLayout);
    allowedFixture.detectChanges();
    const allowedLinks = Array.from(allowedFixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>);
    expect(allowedLinks.some((link) => link.textContent?.includes('Cấu hình thanh toán'))).toBe(true);
  });
});
