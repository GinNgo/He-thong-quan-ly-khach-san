import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '@app/core/services/auth';
import { RegisterComponent } from './register.component';

describe('RegisterComponent email verification message', () => {
  it('directs the new user to the verification email when delivery succeeds', async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            register: vi.fn(() => of({
              message: 'User registered successfully!',
              welcomeEmailSent: false,
              verificationEmailSent: true,
            })),
          },
        },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: {}, queryParams: of({}) } },
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

    expect(component.successMessage).toContain('xác minh email');
  });
});
