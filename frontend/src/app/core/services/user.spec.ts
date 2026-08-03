import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { UserService } from './user';

describe('UserService staff reads', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the tenant-scoped staff endpoint', () => {
    service.getStaff().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/staff`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads property options from the authenticated staff endpoint', () => {
    service.getStaffProperties().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/staff/properties`);
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 10, name: 'LuxeStay Da Nang' }]);
  });

  it('loads only assignable staff roles from the dedicated endpoint', () => {
    service.getStaffRoles().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/staff/roles`);
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 3, code: 'RECEPTIONIST', name: 'Le tan' }]);
  });

  it('creates staff through the dedicated endpoint', () => {
    const payload = {
      username: 'new-staff',
      email: 'new-staff@example.test',
      password: 'StrongPass1',
      fullName: 'New Staff',
      phone: null,
      roleIds: [3],
      hotelId: 10,
    };

    service.createStaff(payload).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/staff`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 42, ...payload });
  });
});
