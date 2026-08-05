import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '@app/core/services/auth';
import { AdminLoginComponent } from './admin-login/admin-login.component';
import { LoginComponent } from './login/login.component';

describe('Login throttling presentation', () => {
  let authService: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    getRoles: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    googleLogin: ReturnType<typeof vi.fn>;
    facebookLogin: ReturnType<typeof vi.fn>;
    setSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    authService = {
      isLoggedIn: vi.fn(() => false),
      getRoles: vi.fn(() => []),
      login: vi.fn(() => of(null)),
      googleLogin: vi.fn(),
      facebookLogin: vi.fn(),
      setSession: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent, AdminLoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: { navigate: vi.fn(), navigateByUrl: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: {} } } },
      ],
    }).compileComponents();
  });

  it('shows a generic public-login wait message and accessible alert for HTTP 429', () => {
    authService.login.mockReturnValue(throwError(() => temporaryBlockError('120')));
    const fixture: ComponentFixture<LoginComponent> = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.loginObj = {
      username: 'known-customer@example.com',
      password: 'not-a-real-password',
    };

    fixture.componentInstance.onSubmit();
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage).toContain('2 phút');
    expect(fixture.componentInstance.errorMessage).not.toContain('known-customer@example.com');
    expect(fixture.componentInstance.errorMessage).not.toContain('Known account is locked');
    expect(authService.setSession).not.toHaveBeenCalled();

    const alert = fixture.nativeElement.querySelector('#login-error') as HTMLElement;
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.getAttribute('aria-live')).toBe('assertive');
    expect(fixture.nativeElement.querySelector('#username').getAttribute('aria-describedby')).toBe('login-error');
    expect(fixture.nativeElement.querySelector('#password').getAttribute('aria-describedby')).toBe('login-error');
  });

  it('shows the same generic contract in admin login without exposing account existence', () => {
    authService.login.mockReturnValue(throwError(() => temporaryBlockError('45')));
    const fixture: ComponentFixture<AdminLoginComponent> = TestBed.createComponent(AdminLoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.loginObj = {
      username: 'unknown-or-known-admin',
      password: 'not-a-real-password',
    };

    fixture.componentInstance.onSubmit();
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage).toContain('45 giây');
    expect(fixture.componentInstance.errorMessage).not.toContain('unknown-or-known-admin');
    expect(fixture.componentInstance.errorMessage).not.toContain('Known account is locked');
    expect(authService.setSession).not.toHaveBeenCalled();

    const alert = fixture.nativeElement.querySelector('#admin-login-error') as HTMLElement;
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.getAttribute('aria-live')).toBe('assertive');
    expect(fixture.nativeElement.querySelector('#username').getAttribute('aria-describedby')).toBe('admin-login-error');
    expect(fixture.nativeElement.querySelector('#password').getAttribute('aria-describedby')).toBe('admin-login-error');
  });
});

function temporaryBlockError(retryAfter: string) {
  return {
    status: 429,
    error: {
      code: 'LOGIN_TEMPORARILY_BLOCKED',
      message: 'Known account is locked.',
    },
    headers: {
      get: (name: string) => name === 'Retry-After' ? retryAfter : null,
    },
  };
}
