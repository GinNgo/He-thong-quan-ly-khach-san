import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ClientApiService } from '../../../core/services/client-api.service';
import { PropertyPaymentService } from '../../../core/services/property-payment.service';
import { BookingCheckoutComponent } from './booking-checkout.component';
import { AsyncActionCoordinatorService } from '../../../core/services/async-action-coordinator.service';

describe('BookingCheckoutComponent', () => {
  let fixture: ComponentFixture<BookingCheckoutComponent>;
  let component: BookingCheckoutComponent;
  let reservation$: Subject<any>;
  let queryParams$: Subject<Record<string, string>>;
  let clientApi: { bookRoom: ReturnType<typeof vi.fn> };
  let paymentApi: {
    createAttempt: ReturnType<typeof vi.fn>;
    getAttempt: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    reservation$ = new Subject<any>();
    queryParams$ = new Subject<Record<string, string>>();
    clientApi = { bookRoom: vi.fn(() => reservation$) };
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

  function setValidBooking(paymentMethod: string): void {
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
