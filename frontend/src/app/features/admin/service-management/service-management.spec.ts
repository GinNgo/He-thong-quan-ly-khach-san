import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { HotelServiceService } from '@app/core/services/hotel-service.service';
import { ManagementApiService } from '@app/core/services/management-api.service';
import { ServiceManagement } from './service-management';

describe('ServiceManagement', () => {
  let fixture: ComponentFixture<ServiceManagement>;
  let component: ServiceManagement;
  let hotelService: { getServices: ReturnType<typeof vi.fn> };
  let managementApi: { context: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    hotelService = { getServices: vi.fn(() => of([])) };
    managementApi = {
      context: vi.fn(() => of({
        properties: [
          { id: 10, code: 'P-10', nameVi: 'Property 10' },
          { id: 20, code: 'P-20', nameVi: 'Property 20' },
        ],
        activePropertyId: 20,
      })),
    };

    await TestBed.configureTestingModule({
      imports: [ServiceManagement],
      providers: [
        { provide: HotelServiceService, useValue: hotelService },
        { provide: ManagementApiService, useValue: managementApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ServiceManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads services for the server-authorized active property', () => {
    expect(component.selectedPropertyId).toBe(20);
    expect(hotelService.getServices).toHaveBeenCalledWith(20);
  });

  it('reloads the catalog when the selected property changes', () => {
    component.selectedPropertyId = 10;
    component.onPropertyChange();

    expect(hotelService.getServices).toHaveBeenLastCalledWith(10);
  });
});
