import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { EmailVerificationService } from '@app/core/services/email-verification.service';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  let fixture: ComponentFixture<VerifyEmailComponent>;
  let verification: { confirm: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    verification = {
      confirm: vi.fn(() => of({ message: 'Verified', emailChanged: false, email: 'guest@example.com' })),
    };
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ token: 'known-token' }) } },
        },
        { provide: EmailVerificationService, useValue: verification },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(VerifyEmailComponent);
  });

  it('confirms a one-time verification token and exposes a success state', () => {
    fixture.detectChanges();

    expect(verification.confirm).toHaveBeenCalledWith('known-token');
    expect(fixture.componentInstance.successMessage).toContain('xác minh');
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('xác minh');
  });

  it('shows the stable API error when the token is expired', () => {
    verification.confirm.mockReturnValue(throwError(() => ({ error: { message: 'Token expired.' } })));

    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage).toBe('Token expired.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Token expired.');
  });
});
