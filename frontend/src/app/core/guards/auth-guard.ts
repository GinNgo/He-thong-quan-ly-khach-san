import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  if (state.url.startsWith('/admin')) {
    router.navigate(['/admin/login']);
  } else {
    router.navigate(['/login']);
  }
  return false;
};
