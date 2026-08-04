import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ReservationService } from './reservation.service';

describe('ReservationService amendment API', () => {
  let service: ReservationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReservationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads customer and staff contexts from separate authorization surfaces', () => {
    service.getAmendmentContext(42).subscribe();
    service.getAmendmentContext(42, true).subscribe();

    expect(http.expectOne(`${environment.apiUrl}/reservations/42/amendment-context`).request.method).toBe('GET');
    expect(http.expectOne(`${environment.apiUrl}/management/reservations/42/amendment-context`).request.method).toBe('GET');
  });

  it('quotes only operational fields and preserves the retry key', () => {
    service.createAmendmentQuote(42, {
      proposedRoomTypeId: 7,
      proposedCheckInDate: '2026-08-10',
      proposedCheckOutDate: '2026-08-12',
      proposedQuantity: 2,
      proposedAdults: 3,
      proposedChildren: 1,
    }, 'quote-key').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/reservations/42/amendment-quotes`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('quote-key');
    expect(request.request.body).toEqual({
      proposedRoomTypeId: 7,
      proposedCheckInDate: '2026-08-10',
      proposedCheckOutDate: '2026-08-12',
      proposedQuantity: 2,
      proposedAdults: 3,
      proposedChildren: 1,
    });
    expect(request.request.body.priceDelta).toBeUndefined();
    expect(request.request.body.totalAmount).toBeUndefined();
    request.flush(quoteResponse());
  });

  it('uses dedicated quote payment and apply endpoints with encoded public ids', () => {
    service.createAmendmentPaymentAttempt(42, 'quote/one', 'SIMULATOR', 'payment-key', true).subscribe();
    service.applyAmendmentQuote(42, 'quote/one', 'apply-key', true).subscribe();

    const payment = http.expectOne(
      `${environment.apiUrl}/management/reservations/42/amendment-quotes/quote%2Fone/payment-attempts`,
    );
    expect(payment.request.body).toEqual({ method: 'SIMULATOR' });
    expect(payment.request.headers.get('Idempotency-Key')).toBe('payment-key');
    payment.flush(quoteResponse());

    const apply = http.expectOne(
      `${environment.apiUrl}/management/reservations/42/amendment-quotes/quote%2Fone/apply`,
    );
    expect(apply.request.body).toBeNull();
    expect(apply.request.headers.get('Idempotency-Key')).toBe('apply-key');
    apply.flush({ ...quoteResponse(), status: 'APPLIED' });
  });

  function quoteResponse() {
    return {
      publicId: 'quote-1',
      reservationId: 42,
      status: 'QUOTED',
      policyVersion: 'RESERVATION_CHANGE_POLICY_V1',
      original: snapshot(1000000, 300000),
      proposed: snapshot(900000, 270000),
      priceDelta: { amount: -100000, currency: 'VND' },
      preservedDiscount: { amount: 0, currency: 'VND' },
      expiresAt: '2026-08-04T03:02:00Z',
      cutoffAt: '2026-08-10T06:55:00Z',
      settlement: {
        type: 'NONE',
        amount: { amount: 0, currency: 'VND' },
        paymentAttempt: null,
        refundRequestPublicId: null,
      },
      replayed: false,
    };
  }

  function snapshot(totalAmount: number, depositRequired: number) {
    return {
      roomTypeId: 7,
      roomTypeName: 'Deluxe',
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      quantity: 1,
      adults: 2,
      children: 0,
      totalAmount: { amount: totalAmount, currency: 'VND' },
      depositRequired: { amount: depositRequired, currency: 'VND' },
    };
  }
});
