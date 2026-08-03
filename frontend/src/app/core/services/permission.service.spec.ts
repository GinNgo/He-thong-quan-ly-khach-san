import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth';
import { ActionCode, FunctionCode, PermissionService } from './permission.service';

describe('PermissionService authoritative UI context', () => {
  let roles: string[];
  let permissions: Array<{ function: string; actionMask: number }>;
  let service: PermissionService;

  beforeEach(() => {
    roles = [];
    permissions = [];
    TestBed.configureTestingModule({
      providers: [
        PermissionService,
        {
          provide: AuthService,
          useValue: {
            getRoles: () => roles,
            getPermissions: () => permissions,
          },
        },
      ],
    });
    service = TestBed.inject(PermissionService);
  });

  it('evaluates the structured mask returned by the auth response', () => {
    permissions = [{ function: FunctionCode.BOOKING, actionMask: ActionCode.VIEW | ActionCode.UPDATE }];

    expect(service.canView(FunctionCode.BOOKING)).toBe(true);
    expect(service.canUpdate(FunctionCode.BOOKING)).toBe(true);
    expect(service.canDelete(FunctionCode.BOOKING)).toBe(false);
  });

  it('does not grant a frontend bypass from username or the ordinary ADMIN role', () => {
    roles = ['ADMIN'];
    expect(service.isSuperAdmin()).toBe(false);
    expect(service.canView(FunctionCode.SYSTEM)).toBe(false);

    roles = ['SUPER_ADMIN'];
    expect(service.isSuperAdmin()).toBe(true);
    expect(service.canView(FunctionCode.SYSTEM)).toBe(true);
  });
});
