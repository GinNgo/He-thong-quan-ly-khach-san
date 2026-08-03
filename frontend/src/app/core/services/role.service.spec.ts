import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { CreateRoleRequest, RoleService, UpdateRoleRequest } from './role.service';

describe('RoleService', () => {
  let service: RoleService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RoleService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the typed create fields to the global role catalog', () => {
    const payload: CreateRoleRequest = {
      code: 'NIGHT_AUDITOR',
      name: 'Night auditor',
      description: 'Night shift'
    };

    service.createRole(payload).subscribe(role => expect(role.status).toBe('ACTIVE'));

    const request = http.expectOne(`${environment.apiUrl}/roles`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...payload, id: 20, status: 'ACTIVE', systemRole: false });
  });

  it('uses the metadata-only update contract', () => {
    const payload: UpdateRoleRequest = {
      code: 'NIGHT_AUDITOR',
      name: 'Night operations',
      description: 'Updated'
    };

    service.updateRole(20, payload).subscribe(role => expect(role.name).toBe('Night operations'));

    const request = http.expectOne(`${environment.apiUrl}/roles/20`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body.status).toBeUndefined();
    request.flush({ ...payload, id: 20, status: 'ACTIVE', systemRole: false });
  });

  it('uses separate endpoints for soft deactivation and reactivation', () => {
    service.deleteRole(20).subscribe();
    const deactivate = http.expectOne(`${environment.apiUrl}/roles/20`);
    expect(deactivate.request.method).toBe('DELETE');
    deactivate.flush(null);

    service.reactivateRole(20).subscribe(role => expect(role.status).toBe('ACTIVE'));
    const reactivate = http.expectOne(`${environment.apiUrl}/roles/20/reactivate`);
    expect(reactivate.request.method).toBe('POST');
    expect(reactivate.request.body).toEqual({});
    reactivate.flush({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' });
  });
});
