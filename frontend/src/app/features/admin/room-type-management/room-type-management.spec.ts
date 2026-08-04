import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { AdminInventoryService } from '../../../core/services/admin-inventory.service';
import { AmenityService } from '../../../core/services/amenity.service';
import { PermissionService } from '../../../core/services/permission.service';
import { RoomTypeManagement } from './room-type-management';

describe('RoomTypeManagement', () => {
  const api = {
    getRoomTypes: vi.fn(() => of([{ id: 4, hotelId: 2, code: 'DLX', nameVi: 'Deluxe', nameEn: 'Deluxe', maxAdults: 2, maxChildren: 0, maxGuests: 2, basePrice: 500000, status: 'ACTIVE' }])),
    getProperties: vi.fn(() => of([{ id: 2, name: 'Hotel 2' }])),
    createRoomType: vi.fn(() => of({ id: 5 })),
    updateRoomType: vi.fn(() => of({ id: 4 })),
    deleteRoomType: vi.fn(() => of(undefined))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [RoomTypeManagement],
      providers: [
        { provide: AdminInventoryService, useValue: api },
        { provide: PermissionService, useValue: { hasPermission: () => true } },
        { provide: AmenityService, useValue: { publicCatalog: () => of([]), assignments: () => of([]), replaceAssignments: () => of([]) } }
      ]
    }).compileComponents();
  });

  it('loads, creates and updates a validated room type', () => {
    const fixture = TestBed.createComponent(RoomTypeManagement);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.roomTypes).toHaveLength(1);

    component.openCreate();
    component.form = { hotelId: 2, code: 'STD', nameVi: 'Tiêu chuẩn', nameEn: 'Standard', maxAdults: 2, maxChildren: 0, maxGuests: 2, basePrice: 300000, status: 'ACTIVE' };
    component.save();
    expect(api.createRoomType).toHaveBeenCalledWith(expect.objectContaining({ code: 'STD', hotelId: 2 }));

    component.openEdit(component.roomTypes[0]);
    component.form.nameVi = 'Deluxe mới';
    component.save();
    expect(api.updateRoomType).toHaveBeenCalledWith(4, expect.objectContaining({ nameVi: 'Deluxe mới' }));
  }, 10000);

  it('does not submit inconsistent capacity', () => {
    const fixture = TestBed.createComponent(RoomTypeManagement);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.form = { hotelId: 2, code: 'BAD', nameVi: 'Sai', maxAdults: 2, maxChildren: 2, maxGuests: 3, basePrice: 100000 };
    component.save();
    expect(api.createRoomType).not.toHaveBeenCalled();
  });

  it('confirms and calls the soft-disable endpoint', () => {
    const fixture = TestBed.createComponent(RoomTypeManagement);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const confirmations = fixture.debugElement.injector.get(ConfirmationService);
    vi.spyOn(confirmations, 'confirm').mockImplementation(options => { options.accept?.(); return confirmations; });

    component.deactivate(component.roomTypes[0]);

    expect(api.deleteRoomType).toHaveBeenCalledWith(4);
  });
});
