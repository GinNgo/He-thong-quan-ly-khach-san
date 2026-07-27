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
      address: '01 Đường Biển',
      starRating: 4
    });
    component.save();

    const create = http.expectOne({ method: 'POST', url: `${environment.apiUrl}/v1/hotels` });
    expect(create.request.body).toMatchObject({
      name: 'LuxeStay T046',
      addressLine: '01 Đường Biển',
      city: 'Đà Nẵng',
      country: 'Việt Nam',
      status: 'DRAFT',
      approvalStatus: 'DRAFT',
      operationStatus: 'INACTIVE',
      isDemo: false
    });
    create.flush({ id: 99, name: 'LuxeStay T046' });
    http.expectOne(`${environment.apiUrl}/v1/hotels`).flush([]);
    fixture.detectChanges();

    expect(component.dialogVisible).toBe(false);
    expect(component.saving).toBe(false);
    fixture.destroy();
  });

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
    component.closeCreate();
    fixture.destroy();
  });
});
