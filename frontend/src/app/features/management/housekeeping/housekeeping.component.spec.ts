import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth';
import { HousekeepingService, HousekeepingTask } from '../../../core/services/housekeeping.service';
import { ManagementApiService } from '../../../core/services/management-api.service';
import { PermissionService } from '../../../core/services/permission.service';
import { HousekeepingComponent } from './housekeeping.component';

describe('HousekeepingComponent', () => {
  const context = {
    properties: [{ id: 10, code: 'P-10', nameVi: 'Property 10', propertyType: 'HOTEL', address: 'Address', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', isDemo: false }],
    activePropertyId: 10,
    activePropertyOperational: true,
    planCode: 'STANDARD', subscriptionStatus: 'ACTIVE', subscriptionSource: 'PLATFORM', lifetime: false,
    limits: {}, usage: {}, upgradeRequired: false,
  };

  let fixture: ComponentFixture<HousekeepingComponent>;
  let housekeeping: {
    list: ReturnType<typeof vi.fn>;
    assignees: ReturnType<typeof vi.fn>;
    claim: ReturnType<typeof vi.fn>;
    assign: ReturnType<typeof vi.fn>;
    start: ReturnType<typeof vi.fn>;
    complete: ReturnType<typeof vi.fn>;
  };
  let permission: { hasPermission: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    housekeeping = {
      list: vi.fn(() => of([])),
      assignees: vi.fn(() => of([])),
      claim: vi.fn(),
      assign: vi.fn(),
      start: vi.fn(),
      complete: vi.fn(),
    };
    permission = { hasPermission: vi.fn(() => true) };
    await TestBed.configureTestingModule({
      imports: [HousekeepingComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: ManagementApiService, useValue: { context: () => of(context) } },
        { provide: HousekeepingService, useValue: housekeeping },
        { provide: AuthService, useValue: { getCurrentUserId: () => 7 } },
        { provide: PermissionService, useValue: permission },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HousekeepingComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders an accessible empty queue state', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Không có tác vụ');
    expect(housekeeping.list).toHaveBeenCalledWith(10, undefined);
  });

  it('renders loading state while the queue request is pending', async () => {
    housekeeping.list.mockReturnValue(throwError(() => new Error('network')));
    const pending = TestBed.createComponent(HousekeepingComponent);
    pending.detectChanges();
    await pending.whenStable();
    expect((pending.nativeElement as HTMLElement).textContent).toContain('Không thể tải hàng đợi');
  });

  it('shows completion only to the assigned user with the approve permission', async () => {
    housekeeping.list.mockReturnValue(of([task()]));
    const assigned = TestBed.createComponent(HousekeepingComponent);
    assigned.detectChanges();
    await assigned.whenStable();
    expect((assigned.nativeElement as HTMLElement).textContent).toContain('Hoàn tất và kiểm tra mở bán');

    permission.hasPermission.mockReturnValue(false);
    const denied = TestBed.createComponent(HousekeepingComponent);
    denied.detectChanges();
    await denied.whenStable();
    expect((denied.nativeElement as HTMLElement).textContent).not.toContain('Hoàn tất và kiểm tra mở bán');
  });

  it('completes with the optimistic version and explains a maintenance-blocked release', async () => {
    const activeTask = task();
    housekeeping.list.mockReturnValue(of([activeTask]));
    housekeeping.complete.mockReturnValue(of({
      ...activeTask,
      status: 'COMPLETED',
      completedAt: '2026-08-04T04:00:00',
      roomStatus: 'MAINTENANCE',
      roomHousekeepingStatus: 'CLEAN',
      roomMaintenanceStatus: 'MAINTENANCE',
      roomReleased: false,
    }));
    const active = TestBed.createComponent(HousekeepingComponent);
    active.detectChanges();
    await active.whenStable();

    const button = Array.from((active.nativeElement as HTMLElement).querySelectorAll('button'))
      .find(item => item.textContent?.includes('Hoàn tất và kiểm tra mở bán')) as HTMLButtonElement;
    button.click();
    active.detectChanges();
    await active.whenStable();

    expect(housekeeping.complete).toHaveBeenCalledWith(41, 6);
    expect((active.nativeElement as HTMLElement).textContent).toContain('vẫn bị chặn bởi MAINTENANCE');
  });

  it('renders the terminal clean and available state', async () => {
    housekeeping.list.mockReturnValue(of([{
      ...task(),
      status: 'COMPLETED',
      completedAt: '2026-08-04T04:00:00',
      roomStatus: 'AVAILABLE',
      roomHousekeepingStatus: 'CLEAN',
      roomReleased: true,
    }]));
    const completed = TestBed.createComponent(HousekeepingComponent);
    completed.detectChanges();
    await completed.whenStable();

    expect((completed.nativeElement as HTMLElement).textContent).toContain('Phòng đã sạch và sẵn sàng mở bán.');
  });

  function task(): HousekeepingTask {
    return {
      id: 41,
      hotelId: 10,
      roomId: 101,
      roomNumber: '101',
      reservationId: 55,
      status: 'IN_PROGRESS',
      assignedToUserId: 7,
      assignedToUsername: 'cleaner',
      assignedToName: 'Cleaner',
      assignedAt: '2026-08-04T03:30:00',
      startedAt: '2026-08-04T03:35:00',
      completedAt: null,
      note: null,
      version: 6,
      staleAssignment: false,
      roomStatus: 'CLEANING',
      roomHousekeepingStatus: 'CLEANING',
      roomMaintenanceStatus: 'NONE',
      roomReleased: false,
    };
  }
});
