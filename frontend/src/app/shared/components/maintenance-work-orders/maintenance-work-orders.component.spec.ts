import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MaintenanceWorkOrderService } from '../../../core/services/maintenance-work-order.service';
import { MaintenanceWorkOrdersComponent } from './maintenance-work-orders.component';

describe('MaintenanceWorkOrdersComponent', () => {
  let fixture: ComponentFixture<MaintenanceWorkOrdersComponent>;
  let component: MaintenanceWorkOrdersComponent;
  const openOrder = { id: 4, propertyId: 7, roomId: 12, reason: 'Dieu hoa hong', priority: 'HIGH', status: 'OPEN', bookingImpact: true, history: [] } as const;
  const api = {
    getAll: vi.fn(() => of([openOrder])),
    create: vi.fn(request => of({ ...openOrder, id: 5, ...request })),
    start: vi.fn(() => of({ ...openOrder, status: 'IN_PROGRESS' })),
    complete: vi.fn(() => of({ ...openOrder, status: 'COMPLETED' })),
    reopen: vi.fn(() => of(openOrder)),
    cancel: vi.fn(() => of({ ...openOrder, status: 'CANCELLED' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MaintenanceWorkOrdersComponent],
      providers: [{ provide: MaintenanceWorkOrderService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(MaintenanceWorkOrdersComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('propertyId', 7);
    fixture.componentRef.setInput('roomId', 12);
    fixture.componentRef.setInput('canCreate', true);
    fixture.componentRef.setInput('canUpdate', true);
    fixture.componentRef.setInput('canCancel', true);
    fixture.detectChanges();
  });

  it('loads tenant-scoped history and exposes valid actions', () => {
    expect(api.getAll).toHaveBeenCalledWith(7, 12);
    expect(component.actions('OPEN')).toEqual(['start', 'cancel']);
    expect(component.actions('IN_PROGRESS')).toEqual(['complete', 'cancel']);
    expect(component.actions('COMPLETED')).toEqual(['reopen']);
  });

  it('creates a reasoned work order with assignment and schedule', () => {
    component.form = { reason: ' Kiem tra ro nuoc ', priority: 'URGENT', assigneeUserId: 22, scheduledStart: '2026-08-05T09:00', scheduledEnd: '2026-08-05T11:00' };
    component.create();
    expect(api.create).toHaveBeenCalledWith({ propertyId: 7, roomId: 12, reason: 'Kiem tra ro nuoc', priority: 'URGENT', assigneeUserId: 22, scheduledStart: '2026-08-05T09:00', scheduledEnd: '2026-08-05T11:00' });
  });

  it('blocks mutations for view-only users', () => {
    component.canCreate = false;
    component.canUpdate = false;
    component.canCancel = false;
    component.form.reason = 'Khong duoc tao';
    component.create();
    component.transition(openOrder as any, 'start');
    expect(api.create).not.toHaveBeenCalled();
    expect(api.start).not.toHaveBeenCalled();
  });
});
