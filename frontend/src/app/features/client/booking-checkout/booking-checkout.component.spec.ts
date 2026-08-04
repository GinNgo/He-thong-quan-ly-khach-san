import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ClientApiService } from '../../../core/services/client-api.service';
import { PropertyPaymentService } from '../../../core/services/property-payment.service';
import { BookingCheckoutComponent } from './booking-checkout.component';
import { AsyncActionCoordinatorService } from '../../../core/services/async-action-coordinator.service';
import { BookingCheckoutRecoveryService } from './booking-checkout-recovery.service';

describe('BookingCheckoutComponent', () => {
  let fixture: ComponentFixture<BookingCheckoutComponent>;
  let component: BookingCheckoutComponent;
  let reservation$: Subject<any>;
  let queryParams$: Subject<Record<string, string>>;
  let clientApi: {
    bookRoom: ReturnType<typeof vi.fn>;
    getReservation: ReturnType<typeof vi.fn>;
  };
  let paymentApi: {
    createAttempt: ReturnType<typeof vi.fn>;
    getAttempt: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    localStorage.clear();
    reservation$ = new Subject<any>();
    queryParams$ = new Subject<Record<string, string>>();
    clientApi = {
      bookRoom: vi.fn(() => reservation$),
      getReservation: vi.fn(),
    };
    paymentApi = {
      createAttempt: vi.fn(),
      getAttempt: vi.fn((attemptId: string) => of({ ...attemptResponse(), attemptId })),
    };

    await TestBed.configureTestingModule({
      imports: [BookingCheckoutComponent],
      providers: [
        { provide: ClientApiService, useValue: clientApi },
        { provide: PropertyPaymentService, useValue: paymentApi },
        { provide: AsyncActionCoordinatorService, useValue: new AsyncActionCoordinatorService() },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ roomTypeId: '1' })),
            queryParams: queryParams$
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookingCheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('blocks checkout when booking context is missing', () => {
    expect(component.bookingContextValid).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Phiên đặt phòng không hợp lệ');

    component.submitBooking();
    expect(clientApi.bookRoom).not.toHaveBeenCalled();
  });

  it('accepts the complete room-selection context after route parameters resolve', () => {
    queryParams$.next({
      checkIn: '2026-08-10',
      checkOut: '2026-08-12',
      adultCount: '2',
      childCount: '0',
      quantity: '1',
      hotelId: '10',
      roomTypeName: 'Deluxe',
      nightlyPrice: '500000',
      estimatedTotal: '1000000'
    });

    expect(component.bookingContextValid).toBe(true);
  });

  it('submits a valid booking only once while the request is pending', () => {
    component.roomTypeId = 1;
    component.hotelId = 10;
    component.roomTypeName = 'Deluxe';
    component.nightlyPrice = 500000;
    component.contextError = '';
    component.bookingData = {
      roomTypeId: 1,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      guests: 2,
      adults: 2,
      children: 0,
      quantity: 1,
      firstName: 'An',
      lastName: 'Nguyen',
      phone: '0900000000',
      paymentMethod: 'PAY_AT_HOTEL',
      specialRequests: ''
    };

    component.submitBooking();
    component.submitBooking();

    expect(clientApi.bookRoom).toHaveBeenCalledTimes(1);
    expect(clientApi.bookRoom.mock.calls[0][1]).toEqual(expect.any(String));
    expect(component.isSubmitting).toBe(true);

    reservation$.next({ id: 77 });
    reservation$.complete();
    expect(component.bookingSuccess).toBe(true);
  });

  it('shares one booking identity with a second tab after an unknown outcome', () => {
    setValidBooking('PAY_AT_HOTEL');
    component.submitBooking();
    const firstKey = clientApi.bookRoom.mock.calls[0][1];
    reservation$.error({ status: 0 });
    reservation$ = new Subject<any>();

    const secondFixture = TestBed.createComponent(BookingCheckoutComponent);
    const secondComponent = secondFixture.componentInstance;
    secondFixture.detectChanges();
    setValidBooking('PAY_AT_HOTEL', secondComponent);
    secondComponent.submitBooking();

    expect(clientApi.bookRoom).toHaveBeenCalledTimes(2);
    expect(clientApi.bookRoom.mock.calls[1][1]).toBe(firstKey);
    secondFixture.destroy();
  });

  it('creates a server-owned deposit attempt after the reservation succeeds', () => {
    const paymentAttempt$ = new Subject<any>();
    paymentApi.createAttempt.mockReturnValue(paymentAttempt$);
    setValidBooking('MOMO');

    component.submitBooking();
    reservation$.next({ id: 77 });

    expect(paymentApi.createAttempt).toHaveBeenCalledWith(
      77,
      { purpose: 'DEPOSIT', method: 'MOMO' },
      { idempotencyKey: expect.any(String) },
    );
    expect(paymentApi.createAttempt.mock.calls[0][1].amount).toBeUndefined();
    paymentAttempt$.next(attemptResponse());
    paymentAttempt$.complete();
    expect(component.paymentAttempt?.expectedAmount).toBe(300000);
    expect(component.bookingSuccess).toBe(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-property-payment-panel')).not.toBeNull();
  });

  it('retries the same attempt request without creating a second reservation', () => {
    const firstAttempt$ = new Subject<any>();
    paymentApi.createAttempt
      .mockReturnValueOnce(firstAttempt$)
      .mockReturnValueOnce(of(attemptResponse()));
    setValidBooking('VNPAY');

    component.submitBooking();
    reservation$.next({ id: 88 });
    firstAttempt$.error({ status: 503 });

    const firstKey = paymentApi.createAttempt.mock.calls[0][2].idempotencyKey;
    component.submitBooking();

    expect(clientApi.bookRoom).toHaveBeenCalledTimes(1);
    expect(paymentApi.createAttempt).toHaveBeenCalledTimes(2);
    expect(paymentApi.createAttempt.mock.calls[1][2].idempotencyKey).toBe(firstKey);
    expect(component.bookingSuccess).toBe(true);
  });

  it('creates a fresh terminal retry without creating a second reservation', () => {
    paymentApi.createAttempt
      .mockReturnValueOnce(of({ ...attemptResponse(), status: 'FAILED' }))
      .mockReturnValueOnce(of({ ...attemptResponse(), attemptId: 'attempt-2' }));
    setValidBooking('MOMO');

    component.submitBooking();
    reservation$.next({ id: 91 });

    const firstKey = paymentApi.createAttempt.mock.calls[0][2].idempotencyKey;
    component.retryPaymentAttempt();

    expect(clientApi.bookRoom).toHaveBeenCalledTimes(1);
    expect(paymentApi.createAttempt).toHaveBeenCalledTimes(2);
    expect(paymentApi.createAttempt.mock.calls[1][0]).toBe(91);
    expect(paymentApi.createAttempt.mock.calls[1][2].idempotencyKey).not.toBe(firstKey);
    expect(component.paymentAttempt?.attemptId).toBe('attempt-2');
  });

  it('resumes an owner-authorized pending attempt after reload without rebooking', () => {
    fixture.destroy();
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'customer' }));
    const recovery = TestBed.inject(BookingCheckoutRecoveryService);
    recovery.save({
      roomTypeId: 1,
      reservationId: 91,
      attemptId: 'attempt-resume',
      paymentMethod: 'MOMO',
      phase: 'PAYMENT_PENDING',
    });
    clientApi.getReservation.mockReturnValue(of({
      id: 91,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      guests: 2,
      totalAmount: 1000000,
      status: 'PENDING_PAYMENT',
      paymentMethod: 'MOMO',
    }));
    paymentApi.getAttempt.mockReturnValue(of({
      ...attemptResponse(),
      attemptId: 'attempt-resume',
      reservationId: 91,
      status: 'PENDING',
    }));

    const resumedFixture = TestBed.createComponent(BookingCheckoutComponent);
    const resumed = resumedFixture.componentInstance;
    resumedFixture.detectChanges();

    expect(clientApi.getReservation).toHaveBeenCalledWith(91);
    expect(paymentApi.getAttempt).toHaveBeenCalledWith('attempt-resume');
    expect(clientApi.bookRoom).not.toHaveBeenCalled();
    expect(resumed.bookingSuccess).toBe(true);
    expect(resumed.checkoutPhase).toBe('PAYMENT_PENDING');
    expect(resumed.paymentAttempt?.attemptId).toBe('attempt-resume');
    resumedFixture.destroy();
  });

  function setValidBooking(
    paymentMethod: string,
    target: BookingCheckoutComponent = component,
  ): void {
    target.roomTypeId = 1;
    target.hotelId = 10;
    target.roomTypeName = 'Deluxe';
    target.nightlyPrice = 500000;
    target.contextError = '';
    target.bookingData = {
      roomTypeId: 1,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      guests: 2,
      adults: 2,
      children: 0,
      quantity: 1,
      firstName: 'An',
      lastName: 'Nguyen',
      phone: '0900000000',
      paymentMethod,
      specialRequests: '',
    };
  }

  function attemptResponse() {
    return {
      attemptId: 'attempt-1',
      reservationId: 77,
      purpose: 'DEPOSIT',
      status: 'PENDING',
      environment: 'SIMULATOR',
      expectedAmount: 300000,
      currency: 'VND',
      expiresAt: '2026-08-10T12:15:00',
      method: 'MOMO',
      provider: 'SIMULATOR',
      receiver: {
        bankName: null,
        bankCode: null,
        accountName: null,
        accountNumberMasked: null,
        qrProvider: null,
        merchantReferenceMasked: null,
        instructionsVi: null,
        instructionsEn: null,
      },
      uniqueTransferContent: null,
      qrData: null,
      redirectUrl: null,
      replayed: false,
    };
  }
});
