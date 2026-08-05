import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth';
import { clientAuthGuard } from './client-auth.guard';

describe('clientAuthGuard', () => {
  let auth: { isLoggedIn: ReturnType<typeof vi.fn>; getRoles: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { isLoggedIn: vi.fn(() => false), getRoles: vi.fn(() => []) };
    router = { createUrlTree: vi.fn((commands, extras) => ({ commands, extras })) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('preserves the exact booking state for an unauthenticated customer', () => {
    const url = '/booking/49?checkIn=2026-08-10&checkOut=2026-08-12&quantity=2';
    const result = TestBed.runInInjectionContext(() => clientAuthGuard({} as never, { url } as never));

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: url } });
    expect(result).toEqual(expect.objectContaining({ commands: ['/login'] }));
  });

  it('allows a customer and rejects a non-customer account for checkout', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.getRoles.mockReturnValue(['CUSTOMER']);
    expect(TestBed.runInInjectionContext(() => clientAuthGuard({} as never, { url: '/booking/49' } as never))).toBe(true);

    auth.getRoles.mockReturnValue(['PROPERTY_OWNER']);
    TestBed.runInInjectionContext(() => clientAuthGuard({} as never, { url: '/booking/49' } as never));
    expect(router.createUrlTree).toHaveBeenLastCalledWith(['/403'], {
      queryParams: { reason: 'CUSTOMER_REQUIRED' },
    });
  });

  it('keeps non-booking authenticated client routes compatible with their existing role rules', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.getRoles.mockReturnValue(['PROPERTY_OWNER']);

    expect(TestBed.runInInjectionContext(() => clientAuthGuard({} as never, { url: '/partner/registration-status' } as never))).toBe(true);
  });
});
