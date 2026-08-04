import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth';
import { PropertyManagementComponent } from './property-management';

describe('PropertyManagementComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyManagementComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getRoles: () => ['SUPER_ADMIN'] } }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('opens the create form and submits a typed draft payload', () => {
    const fixture = TestBed.createComponent(PropertyManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([
      { id: 1, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }
    ]);
    fixture.detectChanges();

    component.openCreate();
    component.form.patchValue({
      nameVi: 'LuxeStay T046',
      propertyType: 'HOTEL',
      provinceId: 1,
      wardId: 10,
      addressLine: '01 Đường Biển',
      latitude: 16.05,
      longitude: 108.2,
      website: 'https://luxestay.example',
      checkinTime: '14:00',
      checkoutTime: '12:00',
      minPrice: 500000,
      maxPrice: 1500000,
      starRating: 4
    });
    component.save();

    const create = http.expectOne({ method: 'POST', url: `${environment.apiUrl}/v1/hotels` });
    expect(create.request.body).toMatchObject({
      nameVi: 'LuxeStay T046',
      addressLine: '01 Đường Biển',
      provinceId: 1,
      wardId: 10,
      latitude: 16.05,
      longitude: 108.2,
      website: 'https://luxestay.example',
      checkinTime: '14:00',
      checkoutTime: '12:00',
      minPrice: 500000,
      maxPrice: 1500000
    });
    expect(create.request.body.status).toBeUndefined();
    expect(create.request.body.approvalStatus).toBeUndefined();
    expect(create.request.body.operationStatus).toBeUndefined();
    create.flush({ id: 99, name: 'LuxeStay T046' });
    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    fixture.detectChanges();

    expect(component.dialogVisible).toBe(false);
    expect(component.saving).toBe(false);
    fixture.destroy();
  }, 15000);

  it('blocks an incomplete form before sending a create request', () => {
    const fixture = TestBed.createComponent(PropertyManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([
      { id: 1, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }
    ]);
    fixture.detectChanges();

    component.openCreate();
    component.save();

    expect(component.formError).toContain('bắt buộc');
    expect(http.match({ method: 'POST', url: `${environment.apiUrl}/v1/hotels` })).toHaveLength(0);
    component.closeDialog();
    fixture.destroy();
  });

  it('round-trips a complete admin profile through the canonical update wrapper', () => {
    const fixture = TestBed.createComponent(PropertyManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([{ id: 1, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }]);

    component.openEdit({
      id: 9, nameVi: 'Old property', propertyType: 'HOTEL', addressLine: 'Old address',
      provinceId: 1, wardId: 10, latitude: 16.05, longitude: 108.2,
      website: 'https://old.example', checkinTime: '14:00', checkoutTime: '12:00'
    });
    http.expectOne(`${environment.apiUrl}/public/locations/provinces/1/wards`).flush([{ id: 10, nameVi: 'Hải Châu', locationType: 'WARD' }]);
    component.form.patchValue({ website: 'https://new.example', minPrice: 600000, maxPrice: 1800000, reason: 'Correct canonical profile' });
    component.save();

    const update = http.expectOne({ method: 'PUT', url: `${environment.apiUrl}/v1/hotels/9` });
    expect(update.request.body.reason).toBe('Correct canonical profile');
    expect(update.request.body.profile).toMatchObject({
      nameVi: 'Old property', addressLine: 'Old address', provinceId: 1, wardId: 10,
      latitude: 16.05, longitude: 108.2, website: 'https://new.example', minPrice: 600000, maxPrice: 1800000
    });
    update.flush({ id: 9 });
    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    fixture.destroy();
  });

  it('renders the property-scoped gallery inside the admin editor', () => {
    const fixture = TestBed.createComponent(PropertyManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([
      { id: 1, nameVi: 'Da Nang', locationType: 'PROVINCE' }
    ]);

    component.openEdit({
      id: 9, nameVi: 'Gallery property', propertyType: 'HOTEL', addressLine: '01 Beach Road',
      provinceId: 1, wardId: 10
    });
    http.expectOne(`${environment.apiUrl}/public/locations/provinces/1/wards`).flush([
      { id: 10, nameVi: 'Hai Chau', locationType: 'WARD' }
    ]);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/v1/properties/9/gallery`).flush([]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Thu vien anh');
    fixture.destroy();
  });
});
