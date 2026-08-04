import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ManagementApiService } from '../../../core/services/management-api.service';
import { PermissionService } from '../../../core/services/permission.service';
import { ManagementInventoryComponent } from './management-inventory.component';

describe('ManagementInventoryComponent', () => {
  let fixture: ComponentFixture<ManagementInventoryComponent>;
  let component: ManagementInventoryComponent;
  let api: {
    context: ReturnType<typeof vi.fn>;
    rooms: ReturnType<typeof vi.fn>;
    roomTypes: ReturnType<typeof vi.fn>;
    startRoomMaintenance: ReturnType<typeof vi.fn>;
    completeRoomMaintenance: ReturnType<typeof vi.fn>;
    operationalExport: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      context: vi.fn(() => of({ properties: [{ id: 3, nameVi: 'Hotel' }], activePropertyId: 3 })),
      rooms: vi.fn(() => of([{ id: 12, roomNumber: '201', roomTypeNameVi: 'Deluxe', floor: 2, status: 'AVAILABLE', maintenanceStatus: 'NONE' }])),
      roomTypes: vi.fn(() => of([])),
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
        { provide: PermissionService, useValue: { hasPermission: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode: 'rooms' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManagementInventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('uses dedicated maintenance commands instead of a generic room update', () => {
    component.toggleMaintenance({ id: 12, status: 'AVAILABLE', maintenanceStatus: 'NONE' });
    component.toggleMaintenance({ id: 12, status: 'MAINTENANCE', maintenanceStatus: 'MAINTENANCE' });

    expect(api.startRoomMaintenance).toHaveBeenCalledWith(12);
    expect(api.completeRoomMaintenance).toHaveBeenCalledWith(12);
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
