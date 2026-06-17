import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 403) {
        // Redirect to forbidden page if 403 is encountered
        router.navigate(['/403']);
      } else if (error.status === 401) {
        // If unauthorized and NOT calling login endpoint, go to login
        if (!req.url.includes('/api/auth/login')) {
          localStorage.removeItem('token');
          localStorage.removeItem('permissions');
          router.navigate(['/login']);
        }
      }
      return throwError(() => error);
    })
  );
};
