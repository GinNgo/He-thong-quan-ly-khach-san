import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ManagementApiService } from '../../../core/services/management-api.service';
import { ManagementInventoryComponent } from './management-inventory.component';
import { PermissionService } from '../../../core/services/permission.service';

describe('ManagementInventoryComponent', () => {
  let fixture: ComponentFixture<ManagementInventoryComponent>;
  let component: ManagementInventoryComponent;
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
  };

  beforeEach(async () => {
    api = {
      context: vi.fn(() => of({ properties: [{ id: 3, nameVi: 'Hotel' }], activePropertyId: 3 })),
      rooms: vi.fn(() => of([{ id: 12, status: 'AVAILABLE', maintenanceStatus: 'NONE' }])),
      roomTypes: vi.fn(() => of([])),
      createRoomType: vi.fn(() => of({ id: 21 })),
      updateRoomType: vi.fn(() => of({ id: 21 })),
      deleteRoomType: vi.fn(() => of(undefined)),
      bulkRooms: vi.fn(() => of({ created: [], failedRoomNumbers: [] })),
      updateRoom: vi.fn(() => of({ id: 12 })),
      deleteRoom: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [ManagementInventoryComponent],
      providers: [
        { provide: ManagementApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode: 'rooms' } } } },
        { provide: PermissionService, useValue: { hasPermission: () => true } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManagementInventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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
});
