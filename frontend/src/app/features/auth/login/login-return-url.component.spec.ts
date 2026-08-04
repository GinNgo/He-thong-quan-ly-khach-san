import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '@app/core/services/auth';
import { LoginComponent } from './login.component';

describe('LoginComponent return-to-checkout contract', () => {
  const checkoutUrl = '/booking/49?checkIn=2026-08-10&checkOut=2026-08-12&quantity=2&hotelId=10';

  it('returns a customer to the exact sanitized checkout URL after login', async () => {
    const router = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    const auth = authStub(false);
    auth.login.mockReturnValue(of({
      accessToken: 'token',
      username: 'customer@example.com',
      roles: ['CUSTOMER'],
      permissions: [],
    }));
    const component = await create(auth, router, checkoutUrl);
    component.loginObj = { username: 'customer@example.com', password: 'secret123' };

    component.onSubmit();

    expect(auth.setSession).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith(checkoutUrl);
  });

  it('rejects an external return URL and falls back inside the application', async () => {
    const router = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    const auth = authStub(false);
    auth.login.mockReturnValue(of({
      accessToken: 'token',
      username: 'customer@example.com',
      roles: ['CUSTOMER'],
      permissions: [],
    }));
    const component = await create(auth, router, '//evil.example/steal');
    component.loginObj = { username: 'customer@example.com', password: 'secret123' };

    component.onSubmit();

    expect(component.returnUrl).toBe('/');
    expect(router.navigate).toHaveBeenCalledWith(['/']);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('honors an already-authenticated customer checkout recovery', async () => {
    const router = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    const auth = authStub(true);
    auth.getRoles.mockReturnValue(['CUSTOMER']);

    await create(auth, router, checkoutUrl);

    expect(router.navigateByUrl).toHaveBeenCalledWith(checkoutUrl);
  });

  it('does not send a non-customer account into customer checkout', async () => {
    const router = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    const auth = authStub(false);
    auth.login.mockReturnValue(of({
      accessToken: 'token',
      username: 'owner@example.com',
      roles: ['PROPERTY_OWNER'],
      permissions: [],
    }));
    const component = await create(auth, router, checkoutUrl);
    component.loginObj = { username: 'owner@example.com', password: 'secret123' };

    component.onSubmit();

    expect(router.navigate).toHaveBeenCalledWith(['/403'], {
      queryParams: { reason: 'CUSTOMER_REQUIRED' },
    });
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  async function create(auth: ReturnType<typeof authStub>, router: object, returnUrl: string) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: { returnUrl } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function authStub(isLoggedIn: boolean) {
    return {
      isLoggedIn: vi.fn(() => isLoggedIn),
      getRoles: vi.fn(() => [] as string[]),
      login: vi.fn((): any => of(null)),
      setSession: vi.fn(),
    };
  }
});
