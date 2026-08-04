import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Subject, of, throwError } from 'rxjs';
import { ManagementApiService } from '../../../core/services/management-api.service';
import { ReservationService } from '../../../core/services/reservation.service';
import { ReservationCreate } from './reservation-create';

describe('ReservationCreate staff booking workflow', () => {
  let fixture: ComponentFixture<ReservationCreate>;
  let reservations: any;
  let router: any;
  const context = { hotelId: 3, hotelName: 'Luxe Demo', customers: [{ id: 8, fullName: 'Anna Guest', username: 'anna', maskedEmail: 'an***@test.local' }], roomTypes: [{ id: 7, code: 'DLX', nameVi: 'Deluxe', nameEn: 'Deluxe', basePrice: 1000000 }], paymentMethods: ['CASH'] };
  const quote = { quoteId: 'q1', hotelId: 3, customerId: 8, roomTypeId: 7, roomTypeName: 'Deluxe', checkInDate: '2026-08-10', checkOutDate: '2026-08-12', quantity: 1, adults: 2, children: 0, paymentMethod: 'CASH', availableRooms: 2, basePrice: 1000000, totalAmount: 2000000, depositAmount: 500000, currency: 'VND', expiresAt: '2026-08-04T17:00:00', status: 'QUOTED', replayed: false };

  beforeEach(async () => {
    reservations = { getStaffBookingContext: vi.fn(() => of(context)), createStaffBookingQuote: vi.fn(() => of(quote)), createStaffBooking: vi.fn(() => of({ id: 42 })) };
    router = { navigate: vi.fn() };
    await TestBed.configureTestingModule({ imports: [ReservationCreate], providers: [
      { provide: ReservationService, useValue: reservations },
      { provide: ManagementApiService, useValue: { context: vi.fn(() => of({ properties: [{ id: 3, nameVi: 'Luxe Demo', operational: true }], activePropertyId: 3 })) } },
      { provide: Router, useValue: router }, { provide: MessageService, useValue: { add: vi.fn() } },
    ] }).compileComponents();
    fixture = TestBed.createComponent(ReservationCreate); fixture.detectChanges();
  });

  function validForm() { Object.assign(fixture.componentInstance.form, { hotelId: 3, customerId: 8, roomTypeId: 7, checkInDate: '2026-08-10', checkOutDate: '2026-08-12', quantity: 1, adults: 2, children: 0, paymentMethod: 'CASH' }); }

  it('does not expose a physical-room selector and loads only booking context', () => {
    expect(fixture.nativeElement.textContent).toContain('Phòng vật lý chỉ gán sau');
    expect(fixture.nativeElement.querySelector('[name="roomId"]')).toBeNull();
    expect(reservations.getStaffBookingContext).toHaveBeenCalledWith(3, '');
  });

  it('shows authoritative totals only after requesting a quote', () => {
    validForm(); fixture.componentInstance.requestQuote(); fixture.detectChanges();
    expect(reservations.createStaffBookingQuote).toHaveBeenCalledWith(expect.objectContaining({ customerId: 8, roomTypeId: 7 }), expect.stringContaining('staff-booking-quote-'));
    expect(fixture.nativeElement.textContent).toContain('2,000,000');
    expect(fixture.nativeElement.textContent).toContain('500,000');
  });

  it('invalidates the quote whenever an input changes', () => {
    validForm(); fixture.componentInstance.requestQuote(); expect(fixture.componentInstance.quote).not.toBeNull();
    fixture.componentInstance.invalidateQuote(); expect(fixture.componentInstance.quote).toBeNull();
  });

  it('prevents duplicate create while the request is pending', () => {
    const pending = new Subject<any>(); reservations.createStaffBooking.mockReturnValue(pending);
    validForm(); fixture.componentInstance.requestQuote(); fixture.componentInstance.createBooking(); fixture.componentInstance.createBooking();
    expect(reservations.createStaffBooking).toHaveBeenCalledTimes(1);
  });

  it('drops a stale quote after a server conflict and requires requote', () => {
    reservations.createStaffBooking.mockReturnValue(throwError(() => ({ status: 409 })));
    validForm(); fixture.componentInstance.requestQuote(); fixture.componentInstance.createBooking();
    expect(fixture.componentInstance.quote).toBeNull();
    expect(fixture.componentInstance.error).toContain('báo giá mới');
  });
});
