import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService, AuthState } from '../../core/services/auth';
import { ManagementApiService, ManagementContext } from '../../core/services/management-api.service';
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
});
