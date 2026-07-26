import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth';
import { errorInterceptor } from './error-interceptor';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let routerSpy: any;
  let authServiceSpy: any;

  beforeEach(() => {
    routerSpy = { navigate: vi.fn(), url: '/' };
    authServiceSpy = { logout: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should navigate to /403 on 403 error if not already at /403', () => {
    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 403'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(403);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/403'], { queryParams: { reason: 'ACCESS_DENIED' } });
  });

  it('should not navigate to /403 on 403 error if already at /403', () => {
    routerSpy.url = '/403';

    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 403'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(403);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should handle 401 error in admin area', () => {
    routerSpy.url = '/admin/dashboard';

    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 401'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin/login']);
  });

  it('should not navigate to /admin/login if already there on 401', () => {
    routerSpy.url = '/admin/login';

    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 401'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should handle 401 error in client area', () => {
    routerSpy.url = '/profile';

    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 401'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/profile' } });
  });

  it('should not navigate to /login if already there on 401', () => {
    routerSpy.url = '/login';

    httpClient.get('/api/test').subscribe({
      next: () => { throw new Error('should have failed with 401'); },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});