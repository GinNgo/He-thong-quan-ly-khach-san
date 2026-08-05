import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ManagementApiService } from '../../../core/services/management-api.service';
import { ManagementInventoryComponent } from './management-inventory.component';
import { PermissionService } from '../../../core/services/permission.service';

describe('ManagementInventoryComponent', () => {
  let fixture: ComponentFixture<ManagementInventoryComponent>;
  let component: ManagementInventoryComponent;
  let allowActions = true;
  let api: {
    context: ReturnType<typeof vi.fn>;
    rooms: ReturnType<typeof vi.fn>;
    roomTypes: ReturnType<typeof vi.fn>;
    createRoomType: ReturnType<typeof vi.fn>;
    updateRoomType: ReturnType<typeof vi.fn>;
    deleteRoomType: ReturnType<typeof vi.fn>;
    bulkRooms: ReturnType<typeof vi.fn>;
    updateRoom: ReturnType<typeof vi.fn>;
    deleteRoom: ReturnType<typeof vi.fn>;
    startRoomMaintenance: ReturnType<typeof vi.fn>;
    completeRoomMaintenance: ReturnType<typeof vi.fn>;
    operationalExport: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    allowActions = true;
    api = {
      context: vi.fn(() => of({ properties: [{ id: 3, nameVi: 'Hotel' }], activePropertyId: 3 })),
      rooms: vi.fn(() => of([{ id: 12, roomNumber: '201', roomTypeNameVi: 'Deluxe', floor: 2, status: 'AVAILABLE', maintenanceStatus: 'NONE' }])),
      roomTypes: vi.fn(() => of([])),
      createRoomType: vi.fn(() => of({ id: 21 })),
      updateRoomType: vi.fn(() => of({ id: 21 })),
      deleteRoomType: vi.fn(() => of(undefined)),
      bulkRooms: vi.fn(() => of({ created: [], failedRoomNumbers: [] })),
      updateRoom: vi.fn(() => of({ id: 12 })),
      deleteRoom: vi.fn(() => of(undefined)),
      startRoomMaintenance: vi.fn(() => of({})),
      completeRoomMaintenance: vi.fn(() => of({})),
      operationalExport: vi.fn(() => of({ blob: new Blob(['roomRef']), filename: 'rooms-property-3.csv', checksum: 'd'.repeat(64), rowCount: 1, schema: 'operational-rooms-v1' })),
    };
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:test', revokeObjectURL: () => undefined });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    await TestBed.configureTestingModule({
      imports: [ManagementInventoryComponent],
      providers: [
        { provide: ManagementApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode: 'rooms' } } } },
        { provide: PermissionService, useValue: { hasPermission: () => allowActions } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManagementInventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('opens the reasoned work-order workflow instead of toggling room state', () => {
    const room = { id: 12, status: 'AVAILABLE', maintenanceStatus: 'NONE' };
    component.openMaintenance(room);
    expect(component.maintenanceRoom).toBe(room);
    component.closeMaintenance();
    expect(component.maintenanceRoom).toBeUndefined();
  });

  it('updates and soft-disables room types through management parity endpoints', () => {
    component.mode = 'room-types';
    component.propertyId = 3;
    component.editRoomType({ id: 21, hotelId: 3, code: 'DLX', nameVi: 'Deluxe', nameEn: 'Deluxe', maxAdults: 2, maxChildren: 0, maxGuests: 2, basePrice: 500000, status: 'ACTIVE' });
    component.roomTypeForm.nameVi = 'Deluxe mới';
    component.save();

    expect(api.updateRoomType).toHaveBeenCalledWith(21, expect.objectContaining({ hotelId: 3, nameVi: 'Deluxe mới' }));

    component.deactivateRoomType({ id: 21, status: 'ACTIVE' });
    expect(api.deleteRoomType).toHaveBeenCalledWith(21);
  });

  it('edits and soft-disables a physical room through permission-parity endpoints', () => {
    component.propertyId = 3;
    component.roomTypes = [{ id: 5, nameVi: 'Deluxe' }];
    component.editRoom({ id: 12, hotelId: 3, roomTypeId: 5, roomNumber: '101', floor: 1, status: 'AVAILABLE' });
    component.roomForm.roomNumber = '102';
    component.save();
    expect(api.updateRoom).toHaveBeenCalledWith(12, expect.objectContaining({ hotelId: 3, roomNumber: '102' }));

    component.deactivateRoom({ id: 12, status: 'AVAILABLE' });
    expect(api.deleteRoom).toHaveBeenCalledWith(12);
  });

  it('hides every physical-room mutation control without action permission', () => {
    fixture.destroy();
    allowActions = false;
    fixture = TestBed.createComponent(ManagementInventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).not.toContain('Tạo hàng loạt');
    expect(text).not.toContain('Sửa');
    expect(text).not.toContain('Ngừng');
    expect(text).not.toContain('Phiếu bảo trì');
  });

  it('exports selected-property operational data and preserves server metadata', () => {
    component.exportDataset = 'ROOMS';
    component.exportOperational();
    fixture.detectChanges();

    expect(api.operationalExport).toHaveBeenCalledWith(3, 'ROOMS', { status: '', from: '', to: '' });
    expect(fixture.nativeElement.textContent).toContain('rooms-property-3.csv');
    expect(fixture.nativeElement.textContent).toContain('operational-rooms-v1');
    expect(fixture.nativeElement.textContent).toContain('SHA-256');
  });
});
