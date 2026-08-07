import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';

import { routes } from '../../app.routes';
import { AuthService } from '../services/auth';
import { roleGuard } from './role-guard';

describe('roleGuard', () => {
  let roles: string[];
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    roles = [];
    router = { createUrlTree: vi.fn((commands) => ({ commands })) };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getRoles: () => roles } },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows an actor with a required role', () => {
    roles = ['PROPERTY_OWNER'];
    const result = runGuard(['PROPERTY_OWNER', 'HOTEL_MANAGER']);

    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('routes a denied actor to the forbidden page', () => {
    roles = ['CUSTOMER'];
    const result = runGuard(['PROPERTY_OWNER', 'HOTEL_MANAGER']);

    expect(router.createUrlTree).toHaveBeenCalledWith(['/403']);
    expect(result).toEqual({ commands: ['/403'] });
  });

  it('protects the management shell with the backend-supported roles', () => {
    const managementRoute = routes.find((route) => route.path === 'management');

    expect(managementRoute?.canActivate).toContain(roleGuard);
    expect(managementRoute?.data?.['roles']).toEqual([
      'PROPERTY_OWNER',
      'HOTEL_ADMIN',
      'HOTEL_MANAGER',
      'HOUSEKEEPING',
      'SUPER_ADMIN',
      'ADMIN',
    ]);
  });

  function runGuard(requiredRoles: string[]) {
    const route = { data: { roles: requiredRoles } } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/management/dashboard' } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, state));
  }
});
