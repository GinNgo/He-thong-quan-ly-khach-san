import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/core/services/auth';
import { ClientApiService, ReservationSummary, UserContext } from '@app/core/services/client-api.service';
import { EmailVerificationService } from '@app/core/services/email-verification.service';
import { Reservation, ReservationService } from '@app/core/services/reservation.service';
import { UserService } from '@app/core/services/user';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent seeded booking read journey', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let getReservationById: ReturnType<typeof vi.fn>;

  const profile: UserContext = {
    id: 42,
    username: 'customer.seeded@example.test',
    email: 'customer.seeded@example.test',
    fullName: 'Khách seeded',
    roles: ['CUSTOMER'],
  };

  const summary: ReservationSummary = {
    id: 801,
    checkInDate: '2026-08-15',
    checkOutDate: '2026-08-17',
    guests: 2,
    totalAmount: 1800000,
    status: 'CONFIRMED',
    paymentMethod: 'VNPAY',
  };

  const detail: Reservation = {
    ...summary,
    userId: profile.id,
    userFullName: profile.fullName,
    details: [],
    events: [{
      id: 91,
      eventType: 'RESERVATION_CREATED',
      reason: 'Reservation created from an authoritative server quote.',
      actorType: 'USER',
      occurredAt: '2026-08-04T08:15:00',
    }],
  };

  beforeEach(async () => {
    getReservationById = vi.fn(() => of(detail));
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: ActivatedRoute, useValue: {
          snapshot: { data: { tab: 'bookings' } }, queryParams: of({ tab: 'bookings' }),
        } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: AuthService, useValue: { updateCurrentUser: vi.fn(), logout: vi.fn() } },
        { provide: ClientApiService, useValue: {
          getProfile: vi.fn(() => of(profile)), getMyBookings: vi.fn(() => of([summary])),
        } },
        { provide: UserService, useValue: { updateProfile: vi.fn(), uploadAvatar: vi.fn() } },
        { provide: EmailVerificationService, useValue: { requestEmailChange: vi.fn(), resend: vi.fn() } },
        { provide: ReservationService, useValue: {
          getReservationById, cancelMyReservation: vi.fn(),
        } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
  });

  it('lists only the signed-in customer bookings and opens event history', () => {
    const detailButton = fixture.nativeElement.querySelector('.booking-detail-btn') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('#801');

    detailButton.click();
    fixture.detectChanges();

    expect(getReservationById).toHaveBeenCalledWith(801);
    expect(fixture.nativeElement.textContent).toContain('Lịch sử chuyến đi');
    expect(fixture.nativeElement.textContent).toContain('Đã tạo đặt phòng');
  });

  it('shows a safe error without removing the booking list', () => {
    getReservationById.mockReturnValue(throwError(() => new Error('offline')));

    fixture.componentInstance.viewBooking(801);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không thể tải chi tiết chuyến đi');
    expect(fixture.nativeElement.textContent).toContain('#801');
  });
});
