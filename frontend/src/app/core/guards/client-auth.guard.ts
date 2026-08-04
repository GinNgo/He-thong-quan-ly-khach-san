import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth';
import { isBookingReturnUrl } from '../auth/client-return-url';

export const clientAuthGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    if (!isBookingReturnUrl(state.url) || authService.getRoles().includes('CUSTOMER')) return true;
    return router.createUrlTree(['/403'], { queryParams: { reason: 'CUSTOMER_REQUIRED' } });
  }

  // Not logged in, redirect to login page with the return url
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
