import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '@app/core/services/auth';
import { ClientApiService, UserContext } from '@app/core/services/client-api.service';
import { ReservationService } from '@app/core/services/reservation.service';
import { UserService } from '@app/core/services/user';
import { EmailVerificationService } from '@app/core/services/email-verification.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent payment and refund states', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let reservationService: { cancelMyReservation: ReturnType<typeof vi.fn> };

  const user: UserContext = {
    id: 7,
    username: 'customer',
    email: 'customer@example.test',
    fullName: 'Customer Test',
    roles: ['CUSTOMER'],
  };

  beforeEach(async () => {
    reservationService = { cancelMyReservation: vi.fn(() => of({ id: 42, status: 'CANCELLED', refunds: [] })) };
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { data: {} }, queryParams: of({}) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: AuthService, useValue: { updateCurrentUser: vi.fn(), logout: vi.fn() } },
        { provide: ClientApiService, useValue: { getProfile: vi.fn(() => of(user)), getMyBookings: vi.fn(() => of([])) } },
        { provide: UserService, useValue: { updateProfile: vi.fn(() => of(user)), uploadAvatar: vi.fn() } },
        { provide: EmailVerificationService, useValue: { requestEmailChange: vi.fn(), resend: vi.fn() } },
        { provide: ReservationService, useValue: reservationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('labels payment states and reconciliation without relying on color alone', () => {
    expect(component.getPaymentLabel({
      provider: 'VNPAY', amount: 200000, currency: 'VND', status: 'PENDING', reconciliationRequired: false,
    })).toBe('Ch\u1edd c\u1ed5ng thanh to\u00e1n');
    expect(component.getPaymentLabel({
      provider: 'VNPAY', amount: 200000, currency: 'VND', status: 'SUCCEEDED', reconciliationRequired: true,
    })).toBe('C\u1ea7n \u0111\u1ed1i so\u00e1t');
    expect(component.getPaymentDescription({
      provider: 'VNPAY', amount: 200000, currency: 'VND', status: 'FAILED', reconciliationRequired: false,
    })).toContain('ch\u01b0a th\u00e0nh c\u00f4ng');
  });

  it('distinguishes requested, provider-pending, succeeded and failed refunds', () => {
    expect(component.getRefundLabel({
      publicId: 'r1', amount: 100000, currency: 'VND', provider: 'MOMO', status: 'REQUESTED', requestedAt: '2026-07-30',
    })).toBe('\u0110\u00e3 t\u1ea1o y\u00eau c\u1ea7u');
    expect(component.getRefundTone({
      publicId: 'r2', amount: 100000, currency: 'VND', provider: 'MOMO', status: 'PENDING_PROVIDER', requestedAt: '2026-07-30',
    })).toBe('warning');
    expect(component.getRefundLabel({
      publicId: 'r3', amount: 100000, currency: 'VND', provider: 'MOMO', status: 'SUCCEEDED', requestedAt: '2026-07-30',
    })).toBe('\u0110\u00e3 ho\u00e0n ti\u1ec1n');
    expect(component.getRefundLabel({
      publicId: 'r4', amount: 100000, currency: 'VND', provider: 'MOMO', status: 'FAILED', requestedAt: '2026-07-30',
    })).toBe('Ho\u00e0n ti\u1ec1n th\u1ea5t b\u1ea1i');
  });

  it('does not promise an immediate 100% refund during cancellation confirmation', () => {
    const confirmSpy = vi.fn(() => false);
    vi.stubGlobal('confirm', confirmSpy);

    component.cancelBooking(42);

    expect(confirmSpy).toHaveBeenCalledWith(expect.not.stringContaining('100%'));
    expect(confirmSpy).toHaveBeenCalledWith(expect.stringContaining('y\u00eau c\u1ea7u ho\u00e0n ti\u1ec1n'));
    expect(reservationService.cancelMyReservation).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
