import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const currentUrl = router.url || '';
      const isAdminArea = currentUrl.startsWith('/admin') || currentUrl.startsWith('/management');
      const isProtectedClientArea = ['/booking', '/profile', '/booking-history', '/my-invoices', '/settings']
        .some(path => currentUrl.startsWith(path));

      if (error.status === 403 && isAdminArea) {
        router.navigate(['/403']);
      } else if (error.status === 401) {
        if (!req.url.includes('/api/auth/login')) {
          authService.logout();
          localStorage.removeItem('permissions');
          // A stale token from another portal must not replace a public page with Login/403.
          if (isAdminArea) router.navigate(['/admin/login']);
          else if (isProtectedClientArea) router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
        }
      }
      return throwError(() => error);
    })
  );
};
