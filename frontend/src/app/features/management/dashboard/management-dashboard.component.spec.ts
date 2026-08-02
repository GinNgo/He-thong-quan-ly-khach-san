import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { ManagementApiService, ManagementContext } from '../../../core/services/management-api.service';
import { ManagementDashboardComponent } from './management-dashboard.component';

describe('ManagementDashboardComponent', () => {
  it('renders loaded context in zoneless mode', async () => {
    const context$ = new Subject<ManagementContext>();

    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: { context: () => context$ } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();

    context$.next({
      properties: [{ id: 1, code: 'HOTEL-1', nameVi: 'LuxeStay Hà Nội', propertyType: 'HOTEL', address: 'Hà Nội', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', isDemo: false }],
      activePropertyId: 1,
      planCode: 'STANDARD',
      subscriptionStatus: 'ACTIVE',
      subscriptionSource: 'PLATFORM',
      lifetime: false,
      limits: { MAX_ROOMS: 50, MAX_PROPERTIES: 1 },
      usage: { rooms: 9, properties: 1 },
      upgradeRequired: false,
      dashboard: { availableRooms: 6, occupiedRooms: 3 },
    });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('Đang tải tổng quan...');
    expect(element.textContent).toContain('LuxeStay Hà Nội');
    expect(element.textContent).toContain('STANDARD');
    expect(element.textContent).toContain('Entitlement source: PLATFORM');
  });

  it('shows approval guidance instead of operational metrics for a pending property', async () => {
    const context$ = new Subject<ManagementContext>();
    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: { context: () => context$ } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();
    context$.next({
      properties: [{
        id: 2,
        code: 'PENDING-2',
        nameVi: 'Cơ sở mới',
        propertyType: 'HOTEL',
        address: 'Huế',
        approvalStatus: 'PENDING_APPROVAL',
        operationStatus: 'INACTIVE',
        operational: false,
        isDemo: false,
      }],
      activePropertyId: 2,
      activePropertyOperational: false,
      planCode: 'NO_PLAN',
      subscriptionStatus: 'NONE',
      subscriptionSource: 'NONE',
      lifetime: false,
      limits: {},
      usage: { properties: 1 },
      upgradeRequired: true,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Chưa thể vận hành');
    expect(text).toContain('PENDING_APPROVAL');
    expect(text).not.toContain('Phòng trống');
  });
});
