import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { CreateRoleRequest, isGovernedSystemRole, RoleService, UpdateRoleRequest } from './role.service';

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
      description: 'Night shift',
      reason: 'Night coverage'
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
      description: 'Updated',
      expectedVersion: 3,
      reason: 'Operations restructure'
    };

    service.updateRole(20, payload).subscribe(role => expect(role.name).toBe('Night operations'));

    const request = http.expectOne(`${environment.apiUrl}/roles/20`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body.status).toBeUndefined();
    request.flush({ ...payload, id: 20, status: 'ACTIVE', systemRole: false });
  });

  it('uses separate endpoints for soft deactivation and reactivation', () => {
    service.deleteRole(20, { expectedVersion: 3, reason: 'Role retired' }).subscribe();
    const deactivate = http.expectOne(`${environment.apiUrl}/roles/20`);
    expect(deactivate.request.method).toBe('DELETE');
    expect(deactivate.request.body).toEqual({ expectedVersion: 3, reason: 'Role retired' });
    deactivate.flush(null);

    service.reactivateRole(20, { expectedVersion: 4, reason: 'Role restored' })
      .subscribe(role => expect(role.status).toBe('ACTIVE'));
    const reactivate = http.expectOne(`${environment.apiUrl}/roles/20/reactivate`);
    expect(reactivate.request.method).toBe('POST');
    expect(reactivate.request.body).toEqual({ expectedVersion: 4, reason: 'Role restored' });
    reactivate.flush({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' });
  });

  it('recognizes seeded role codes even when persisted flags are stale', () => {
    expect(isGovernedSystemRole({
      id: 7,
      code: ' receptionist ',
      name: 'Receptionist',
      status: 'INACTIVE',
      systemRole: false,
      roleType: 'CUSTOM'
    })).toBe(true);
    expect(isGovernedSystemRole({
      id: 20,
      code: 'NIGHT_AUDITOR',
      name: 'Night auditor',
      status: 'ACTIVE',
      systemRole: false,
      roleType: 'CUSTOM'
    })).toBe(false);
  });
});
