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
      activePropertyOperational: true,
      planCode: 'STANDARD',
      subscriptionStatus: 'ACTIVE',
      subscriptionSource: 'PLATFORM',
      entitlementAuthoritative: true,
      entitlementReference: 'CONTRACT:88',
      lifetime: false,
      limits: { MAX_ROOMS: 50, MAX_PROPERTIES: 1 },
      usage: { rooms: 9, properties: 1 },
      scope: 'SELECTED_PROPERTY',
      generatedAt: '2026-08-04T06:00:00Z',
      sourceWatermark: 'PROPERTY:1',
      upgradeRequired: false,
      dashboard: { totalRooms: 9, availableRooms: 6, reservedRooms: 0, occupiedRooms: 3, dirtyRooms: 0, maintenanceRooms: 0, unclassifiedRooms: 0, pendingHousekeeping: 0, classifiedRooms: 9, reconciliationStatus: 'RECONCILED', countBasis: 'ROOM_STATUS_BY_SELECTED_PROPERTY' },
    });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('Đang tải tổng quan...');
    expect(element.textContent).toContain('LuxeStay Hà Nội');
    expect(element.textContent).toContain('STANDARD');
    expect(element.textContent).toContain('PLATFORM');
    expect(element.textContent).toContain('authoritative');
    expect(element.textContent).toContain('9 đã phân loại + 0 chưa phân loại = 9 tổng');
  });

  it('uses the selected property id and hides prior tenant data when the switch is denied', async () => {
    const requests: Array<number | undefined> = [];
    const responses = [new Subject<ManagementContext>(), new Subject<ManagementContext>()];
    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: { context: (id?: number) => { requests.push(id); return responses[requests.length - 1]; } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();
    responses[0].next({
      properties: [
        { id: 1, code: 'ONE', nameVi: 'Cơ sở Một', propertyType: 'HOTEL', address: 'Hà Nội', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false },
        { id: 2, code: 'TWO', nameVi: 'Cơ sở Hai', propertyType: 'HOTEL', address: 'Huế', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false },
      ],
      activePropertyId: 1, activePropertyOperational: true, planCode: 'STANDARD', subscriptionStatus: 'ACTIVE', lifetime: false,
      limits: { MAX_ROOMS: 20 }, usage: { rooms: 4 }, upgradeRequired: false,
      dashboard: { totalRooms: 4, availableRooms: 4, reservedRooms: 0, occupiedRooms: 0, dirtyRooms: 0, maintenanceRooms: 0, unclassifiedRooms: 0, pendingHousekeeping: 0, classifiedRooms: 4, reconciliationStatus: 'RECONCILED', countBasis: 'ROOM_STATUS_BY_SELECTED_PROPERTY' },
    });
    await fixture.whenStable();

    fixture.componentInstance.selectedPropertyId = 999;
    fixture.componentInstance.selectProperty();
    responses[1].error({ error: { message: 'Không tìm thấy cơ sở.' } });
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(requests).toEqual([undefined, 999]);
    expect(text).toContain('Không tìm thấy cơ sở.');
    expect(text).not.toContain('4 đã phân loại');
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
