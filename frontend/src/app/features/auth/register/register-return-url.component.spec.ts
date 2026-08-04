import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '@app/core/services/auth';
import { RegisterComponent } from './register.component';

describe('RegisterComponent return-to-checkout contract', () => {
  it('preserves a sanitized checkout URL when registration continues to login', async () => {
    vi.useFakeTimers();
    const checkoutUrl = '/booking/49?checkIn=2026-08-10&checkOut=2026-08-12&quantity=2';
    const router = { navigate: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            register: vi.fn(() => of({ message: 'Created', welcomeEmailSent: true })),
          },
        },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: { returnUrl: checkoutUrl } } } },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(RegisterComponent).componentInstance;
    component.registerObj = {
      fullName: 'Guest Test',
      email: 'guest@example.com',
      password: 'secret123',
      confirmPassword: 'secret123',
      countryCode: '+84',
      phone: '901234567',
      terms: true,
    };

    component.onSubmit();
    vi.advanceTimersByTime(2000);

    expect(component.returnUrl).toBe(checkoutUrl);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: checkoutUrl },
    });
    vi.useRealTimers();
  });
});
