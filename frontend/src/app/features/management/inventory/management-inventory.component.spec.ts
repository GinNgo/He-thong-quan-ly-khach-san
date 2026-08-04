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
    startRoomMaintenance: ReturnType<typeof vi.fn>;
    completeRoomMaintenance: ReturnType<typeof vi.fn>;
    createRoomType: ReturnType<typeof vi.fn>;
    updateRoomType: ReturnType<typeof vi.fn>;
    deleteRoomType: ReturnType<typeof vi.fn>;
    bulkRooms: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      context: vi.fn(() => of({ properties: [{ id: 3, nameVi: 'Hotel' }], activePropertyId: 3 })),
      rooms: vi.fn(() => of([{ id: 12, status: 'AVAILABLE', maintenanceStatus: 'NONE' }])),
      roomTypes: vi.fn(() => of([])),
      startRoomMaintenance: vi.fn(() => of({})),
      completeRoomMaintenance: vi.fn(() => of({})),
      createRoomType: vi.fn(() => of({ id: 21 })),
      updateRoomType: vi.fn(() => of({ id: 21 })),
      deleteRoomType: vi.fn(() => of(undefined)),
      bulkRooms: vi.fn(() => of([])),
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

  it('uses dedicated maintenance commands instead of a generic room update', () => {
    component.toggleMaintenance({ id: 12, status: 'AVAILABLE', maintenanceStatus: 'NONE' });
    component.toggleMaintenance({ id: 12, status: 'MAINTENANCE', maintenanceStatus: 'MAINTENANCE' });

    expect(api.startRoomMaintenance).toHaveBeenCalledWith(12);
    expect(api.completeRoomMaintenance).toHaveBeenCalledWith(12);
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
});
