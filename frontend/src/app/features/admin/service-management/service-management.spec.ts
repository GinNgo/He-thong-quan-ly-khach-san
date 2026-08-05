import { registerLocaleData } from '@angular/common';
import localeVi from '@angular/common/locales/vi';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { HotelServiceService } from '@app/core/services/hotel-service.service';
import { ManagementApiService } from '@app/core/services/management-api.service';
import { PermissionService } from '@app/core/services/permission.service';
import { ServiceManagement } from './service-management';

registerLocaleData(localeVi);

describe('ServiceManagement', () => {
  let fixture: ComponentFixture<ServiceManagement>;
  let component: ServiceManagement;
  let hotelService: {
    getServices: ReturnType<typeof vi.fn>;
    createService: ReturnType<typeof vi.fn>;
    updateService: ReturnType<typeof vi.fn>;
    deleteService: ReturnType<typeof vi.fn>;
  };
  let permissionService: { hasPermission: ReturnType<typeof vi.fn> };
  const catalogItem = { id: 4, hotelId: 20, code: 'BREAKFAST', nameVi: 'Bữa sáng', nameEn: 'Breakfast', price: 120000, status: 'ACTIVE', systemService: false };

  beforeEach(async () => {
    hotelService = {
      getServices: vi.fn(() => of([catalogItem])),
      createService: vi.fn(request => of({ id: 5, ...request })),
      updateService: vi.fn((_id, request) => of(request)),
      deleteService: vi.fn(() => of(undefined)),
    };
    permissionService = { hasPermission: vi.fn(() => true) };
    await TestBed.configureTestingModule({
      imports: [ServiceManagement],
      providers: [
        { provide: HotelServiceService, useValue: hotelService },
        { provide: ManagementApiService, useValue: { context: vi.fn(() => of({ properties: [{ id: 10, nameVi: 'Property 10' }, { id: 20, nameVi: 'Property 20' }], activePropertyId: 20 })) } },
        { provide: PermissionService, useValue: permissionService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ServiceManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads services for the server-authorized active property', () => {
    expect(component.selectedPropertyId).toBe(20);
    expect(hotelService.getServices).toHaveBeenCalledWith(20);
    expect(component.services).toEqual([catalogItem]);
  });

  it('validates and creates a localized service without mutable hotel ownership', () => {
    component.openCreate();
    component.form = { code: ' airport_transfer ', nameVi: 'Đưa đón sân bay', nameEn: 'Airport transfer', price: 350000, descriptionVi: 'Một chiều', descriptionEn: 'One way', status: 'ACTIVE' };
    component.save();
    expect(hotelService.createService).toHaveBeenCalledWith(expect.objectContaining({ code: 'AIRPORT_TRANSFER', nameVi: 'Đưa đón sân bay', nameEn: 'Airport transfer', price: 350000 }), 20);
    expect(hotelService.createService.mock.calls[0][0].hotelId).toBeUndefined();
  });

  it('rejects invalid localized input before mutation', () => {
    component.openCreate();
    component.form = { code: '!', nameVi: '', nameEn: 'A', price: 1.5, descriptionVi: '', descriptionEn: '', status: 'ACTIVE' };
    component.save();
    expect(component.submitted).toBe(true);
    expect(hotelService.createService).not.toHaveBeenCalled();
  });

  it('edits and confirms soft deactivation for a mutable catalog item', () => {
    component.openEdit(catalogItem);
    component.form.price = 150000;
    component.save();
    expect(hotelService.updateService).toHaveBeenCalledWith(4, expect.objectContaining({ price: 150000 }));

    const confirmations = fixture.debugElement.injector.get(ConfirmationService);
    vi.spyOn(confirmations, 'confirm').mockImplementation(options => { options.accept?.(); return confirmations; });
    vi.spyOn(globalThis, 'prompt').mockReturnValue('Khong con cung cap');
    component.deactivate(catalogItem);
    expect(hotelService.deleteService).toHaveBeenCalledWith(4, 'Khong con cung cap');
  });

  it('shows a retryable error when catalog loading fails', () => {
    hotelService.getServices.mockReturnValueOnce(throwError(() => ({ error: { message: 'Mất kết nối' } })));
    component.loadServices();
    expect(component.loading).toBe(false);
    expect(component.errorMessage).toBe('Mất kết nối');
  });

  it('hides mutation controls when HOTEL_SERVICE actions are denied', () => {
    permissionService.hasPermission.mockReturnValue(false);
    const restrictedFixture = TestBed.createComponent(ServiceManagement);
    restrictedFixture.detectChanges();
    expect(restrictedFixture.componentInstance.canCreate).toBe(false);
    expect(restrictedFixture.componentInstance.canUpdate).toBe(false);
    expect(restrictedFixture.componentInstance.canDelete).toBe(false);
    expect(restrictedFixture.nativeElement.querySelector('[aria-label="Chỉnh sửa dịch vụ"]')).toBeNull();
  });
});
