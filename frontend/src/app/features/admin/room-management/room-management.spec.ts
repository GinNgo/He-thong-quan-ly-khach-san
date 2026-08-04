import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { AdminInventoryService } from '../../../core/services/admin-inventory.service';
import { PermissionService } from '../../../core/services/permission.service';
import { RoomManagement } from './room-management';

describe('RoomManagement', () => {
  const room = { id: 1, hotelId: 7, roomTypeId: 3, roomNumber: '101', floor: 1, status: 'AVAILABLE', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' };
  const api = {
    getRooms: vi.fn(() => of([room])), getRoomTypes: vi.fn(() => of([{ id: 3, hotelId: 7, code: 'DLX', nameVi: 'Deluxe', maxGuests: 2, basePrice: 1, status: 'ACTIVE' }])),
    getProperties: vi.fn(() => of([{ id: 7, name: 'Hotel 7' }])), createRoom: vi.fn(() => of(room)), updateRoom: vi.fn(() => of(room)),
    bulkCreateRooms: vi.fn(() => of({ created: [room], failedRoomNumbers: [] })), deleteRoom: vi.fn(() => of(undefined)),
    startRoomMaintenance: vi.fn(() => of(room)), completeRoomMaintenance: vi.fn(() => of(room))
  };
  beforeEach(async () => { vi.clearAllMocks(); await TestBed.configureTestingModule({ imports: [RoomManagement], providers: [
    { provide: AdminInventoryService, useValue: api }, { provide: PermissionService, useValue: { hasPermission: () => true } }
  ] }).compileComponents(); });

  it('loads and submits tenant-scoped create and update payloads', () => {
    const fixture = TestBed.createComponent(RoomManagement); fixture.detectChanges(); const component = fixture.componentInstance;
    expect(component.rooms).toHaveLength(1);
    component.form = { hotelId: 7, roomTypeId: 3, roomNumber: '102', floor: 1 }; component.save();
    expect(api.createRoom).toHaveBeenCalledWith(expect.objectContaining({ hotelId: 7, roomTypeId: 3 }));
    component.openEdit(room as any); component.form.roomNumber = '103'; component.save();
    expect(api.updateRoom).toHaveBeenCalledWith(1, expect.objectContaining({ roomNumber: '103' }));
  }, 30000);

  it('rejects oversized bulk ranges before calling the API', () => {
    const fixture = TestBed.createComponent(RoomManagement); fixture.detectChanges(); const component = fixture.componentInstance;
    component.bulk = { hotelId: 7, roomTypeId: 3, floor: 1, fromNumber: 1, toNumber: 201, prefix: '' }; component.createBulk();
    expect(api.bulkCreateRooms).not.toHaveBeenCalled();
  });

  it('submits one bulk request and confirmed soft-disable', () => {
    const fixture = TestBed.createComponent(RoomManagement); fixture.detectChanges(); const component = fixture.componentInstance;
    component.bulk = { hotelId: 7, roomTypeId: 3, floor: 1, fromNumber: 101, toNumber: 103, prefix: 'A' }; component.createBulk();
    expect(api.bulkCreateRooms).toHaveBeenCalledWith(component.bulk);
    const confirmations = fixture.debugElement.injector.get(ConfirmationService);
    vi.spyOn(confirmations, 'confirm').mockImplementation(options => { options.accept?.(); return confirmations; });
    component.deactivate(room as any); expect(api.deleteRoom).toHaveBeenCalledWith(1);
  });
});
