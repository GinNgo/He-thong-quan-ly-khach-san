import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { environment } from '../../../../environments/environment';
import { PartnerRegisterComponent } from './partner-register.component';

describe('PartnerRegisterComponent', () => {
  let fixture: ComponentFixture<PartnerRegisterComponent>;
  let component: PartnerRegisterComponent;
  let http: HttpTestingController;
  let router: Router;
  let authService: { isLoggedIn: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authService = { isLoggedIn: vi.fn(() => false) };
    await TestBed.configureTestingModule({
      imports: [PartnerRegisterComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PartnerRegisterComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  it('loads canonical provinces and resets the ward when province changes', () => {
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([
      { id: 10, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }
    ]);

    component.registerForm.controls.wardId.setValue(99);
    component.registerForm.controls.provinceId.setValue(10);
    component.onProvinceChange();

    expect(component.registerForm.controls.wardId.value).toBeNull();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces/10/wards`).flush([
      { id: 11, nameVi: 'Hải Châu', locationType: 'WARD', parent: { id: 10 } }
    ]);
    expect(component.wards.map(ward => ward.id)).toEqual([11]);
  });

  it('marks canonical property fields invalid without sending a request', () => {
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([]);

    component.onSubmit();

    expect(component.registerForm.controls.provinceId.touched).toBe(true);
    expect(component.registerForm.controls.wardId.touched).toBe(true);
    expect(component.registerForm.controls.address.touched).toBe(true);
    http.expectNone(`${environment.apiUrl}/partner/register`);
  });

  it('submits only the validated anonymous registration payload', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([]);
    component.registerForm.setValue({
      email: ' owner@example.com ',
      password: 'secret123',
      fullName: ' Partner Owner ',
      phone: ' 0900000000 ',
      propertyName: ' Seaside Hotel ',
      provinceId: 10,
      wardId: 11,
      address: ' 12 Bach Dang '
    });

    component.onSubmit();

    const request = http.expectOne(`${environment.apiUrl}/partner/register`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'owner@example.com',
      password: 'secret123',
      fullName: 'Partner Owner',
      phone: '0900000000',
      propertyName: 'Seaside Hotel',
      provinceId: 10,
      wardId: 11,
      address: '12 Bach Dang'
    });
    expect(request.request.body).not.toHaveProperty('propertyAddress');
    expect(request.request.body).not.toHaveProperty('city');
    expect(request.request.body).not.toHaveProperty('country');
    request.flush({ userId: 7, propertyId: 9, status: 'DRAFT' });

    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { registration: 'partner-draft' }
    });
  });

  it('uses authenticated conversion without requiring or sending account credentials', () => {
    authService.isLoggedIn.mockReturnValue(true);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([]);
    component.registerForm.patchValue({
      email: 'must-not-be-sent@example.com',
      password: 'must-not-be-sent',
      fullName: 'Must Not Be Sent',
      phone: '0900000000',
      propertyName: ' Existing Customer Hotel ',
      provinceId: 10,
      wardId: 11,
      address: ' 18 Nguyen Hue '
    });

    component.onSubmit();

    const request = http.expectOne(`${environment.apiUrl}/partner/convert`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      propertyName: 'Existing Customer Hotel',
      provinceId: 10,
      wardId: 11,
      address: '18 Nguyen Hue'
    });
    expect(request.request.body).not.toHaveProperty('email');
    expect(request.request.body).not.toHaveProperty('password');
    expect(request.request.body).not.toHaveProperty('fullName');
    expect(request.request.body).not.toHaveProperty('phone');
    http.expectNone(`${environment.apiUrl}/partner/register`);
    request.flush({ userId: 7, propertyId: 9, status: 'DRAFT' });

    expect(navigate).toHaveBeenCalledWith(['/partner/registration-status']);
    expect(fixture.nativeElement.querySelector('#email')).toBeNull();
    expect(fixture.nativeElement.querySelector('#password')).toBeNull();
  });

  it('shows the backend duplicate-email field error', () => {
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([]);
    component.registerForm.setValue({
      email: 'owner@example.com',
      password: 'secret123',
      fullName: 'Partner Owner',
      phone: '0900000000',
      propertyName: 'Seaside Hotel',
      provinceId: 10,
      wardId: 11,
      address: '12 Bach Dang'
    });

    component.onSubmit();
    http.expectOne(`${environment.apiUrl}/partner/register`).flush({
      code: 'EMAIL_ALREADY_EXISTS',
      message: 'An account with this email already exists.',
      fieldErrors: { email: 'Email is already registered.' }
    }, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage).toBe('Email is already registered.');
  });
});
