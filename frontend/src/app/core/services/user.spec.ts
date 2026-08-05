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

  it('updates staff through the dedicated endpoint', () => {
    const payload = {
      fullName: 'Updated Staff',
      phone: '0901000000',
      password: null,
      roleIds: [3],
      hotelId: 11,
      assignmentReason: 'Transfer to Hue',
      expectedVersion: 4,
      changeReason: 'Approved staff transfer',
    };

    service.updateStaff(42, payload).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/staff/42`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 42, ...payload });
  });

  it('sends versioned staff lifecycle requests to the dedicated endpoints', () => {
    const payload = { hotelId: 11, reason: 'Season ended', expectedVersion: 5 };

    service.deactivateStaff(42, payload).subscribe();
    const deactivate = http.expectOne(`${environment.apiUrl}/users/42/deactivate`);
    expect(deactivate.request.method).toBe('POST');
    expect(deactivate.request.body).toEqual(payload);
    deactivate.flush({ id: 42, version: 6, status: 'INACTIVE' });

    service.reactivateStaff(42, { ...payload, reason: 'Season reopened', expectedVersion: 6 }).subscribe();
    const reactivate = http.expectOne(`${environment.apiUrl}/users/42/reactivate`);
    expect(reactivate.request.method).toBe('POST');
    expect(reactivate.request.body).toEqual({
      hotelId: 11,
      reason: 'Season reopened',
      expectedVersion: 6,
    });
    reactivate.flush({ id: 42, version: 7, status: 'ACTIVE' });
  });
});
