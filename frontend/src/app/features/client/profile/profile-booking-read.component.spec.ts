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
  let originalLanguage: string;

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
    details: [{
      id: 1,
      reservationId: 801,
      roomId: 11,
      roomTypeId: 7,
      roomTypeName: 'Deluxe',
      quantity: 1,
      assignedRoomIds: [11],
      assignedRoomNumbers: ['101'],
    }],
    events: [{
      id: 91,
      eventType: 'RESERVATION_CREATED',
      reason: 'Reservation created from an authoritative server quote.',
      actorType: 'USER',
      occurredAt: '2026-08-04T08:15:00',
    }],
  };

  beforeEach(async () => {
    originalLanguage = document.documentElement.lang;
    document.documentElement.lang = 'vi';
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

  afterEach(() => {
    document.documentElement.lang = originalLanguage;
  });

  it('lists only the signed-in customer bookings and opens event history', () => {
    const detailButton = fixture.nativeElement.querySelector('.booking-detail-btn') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('#801');

    detailButton.click();
    fixture.detectChanges();

    expect(getReservationById).toHaveBeenCalledWith(801);
    expect(fixture.nativeElement.textContent).toContain('Lịch sử chuyến đi');
    expect(fixture.nativeElement.textContent).toContain('Đã tạo đặt phòng');
    expect(fixture.nativeElement.textContent).toContain('Phòng đã được cơ sở sắp xếp');
    expect(fixture.nativeElement.textContent).toContain('101');
  });

  it('shows a pending assignment state without exposing staff mutation controls', () => {
    getReservationById.mockReturnValue(of({
      ...detail,
      details: [{ ...detail.details[0], assignedRoomIds: [], assignedRoomNumbers: [] }],
    }));

    fixture.componentInstance.viewBooking(801);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chưa có phòng vật lý được gán');
    expect(fixture.nativeElement.querySelector('[data-action="apply-room-assignment"]')).toBeNull();
  });

  it('shows a safe error without removing the booking list', () => {
    getReservationById.mockReturnValue(throwError(() => new Error('offline')));

    fixture.componentInstance.viewBooking(801);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không thể tải chi tiết chuyến đi');
    expect(fixture.nativeElement.textContent).toContain('#801');
  });
});
