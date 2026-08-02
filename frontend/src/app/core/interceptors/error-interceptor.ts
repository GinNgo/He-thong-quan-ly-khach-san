import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { isApiError } from '../../shared/financial/financial.models';
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

      if (error.status === 403) {
        const errCode = isApiError(error.error) ? error.error.code : 'ACCESS_DENIED';
        if (!currentUrl.includes('/403')) {
          router.navigate(['/403'], { queryParams: { reason: errCode } });
        }
      } else if (error.status === 401) {
        if (!req.url.includes('/api/auth/login')) {
          authService.logout();
          localStorage.removeItem('permissions');
          // A stale token from another portal must not replace a public page with Login/403.
          if (isAdminArea && !currentUrl.includes('/admin/login')) {
            router.navigate(['/admin/login']);
          } else if (isProtectedClientArea && !currentUrl.includes('/login')) {
            router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
          }
        }
      }
      return throwError(() => error);
    })
  );
};
