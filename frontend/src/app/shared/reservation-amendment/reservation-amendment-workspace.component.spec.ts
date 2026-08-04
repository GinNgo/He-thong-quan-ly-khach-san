import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { ReservationAmendmentQuote, ReservationService } from '../../core/services/reservation.service';
import { ReservationAmendmentWorkspaceComponent } from './reservation-amendment-workspace.component';

describe('ReservationAmendmentWorkspaceComponent', () => {
  let fixture: ComponentFixture<ReservationAmendmentWorkspaceComponent>;
  let component: ReservationAmendmentWorkspaceComponent;
  let service: {
    getAmendmentContext: ReturnType<typeof vi.fn>;
    createAmendmentQuote: ReturnType<typeof vi.fn>;
    getAmendmentQuote: ReturnType<typeof vi.fn>;
    createAmendmentPaymentAttempt: ReturnType<typeof vi.fn>;
    applyAmendmentQuote: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-04T03:00:00Z'));
    service = {
      getAmendmentContext: vi.fn(() => of(contextResponse())),
      createAmendmentQuote: vi.fn(() => of(quoteResponse())),
      getAmendmentQuote: vi.fn(() => of(quoteResponse())),
      createAmendmentPaymentAttempt: vi.fn(() => of(quoteResponse())),
      applyAmendmentQuote: vi.fn(() => of({ ...quoteResponse(), status: 'APPLIED' })),
    };
    await TestBed.configureTestingModule({
      imports: [ReservationAmendmentWorkspaceComponent],
      providers: [{ provide: ReservationService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(ReservationAmendmentWorkspaceComponent);
    component = fixture.componentInstance;
    component.reservationId = 42;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('initializes the form from the server context and renders a server quote', () => {
    expect(component.form.getRawValue()).toEqual({
      proposedRoomTypeId: 7,
      proposedCheckInDate: '2026-08-10',
      proposedCheckOutDate: '2026-08-12',
      proposedQuantity: 1,
      proposedAdults: 2,
      proposedChildren: 0,
    });

    const quoteButton = fixture.nativeElement.querySelector('.amendment__primary') as HTMLButtonElement;
    quoteButton.click();
    fixture.detectChanges();

    expect(service.createAmendmentQuote).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Chênh lệch giá');
    expect(fixture.nativeElement.textContent).toContain('100.000');
  });

  it('expires a two-minute quote and disables confirmation', () => {
    const quoteButton = fixture.nativeElement.querySelector('.amendment__primary') as HTMLButtonElement;
    quoteButton.click();
    fixture.detectChanges();
    vi.advanceTimersByTime(120_000);
    fixture.detectChanges();

    expect(component.expired).toBe(true);
    expect(component.primaryLabel).toBe('Báo giá đã hết hạn');
    const primary = fixture.nativeElement.querySelector('.quote-card footer .amendment__primary') as HTMLButtonElement;
    expect(primary.disabled).toBe(true);
  });

  it('prevents duplicate quote submissions while the first result is unresolved', () => {
    const pending = new Subject<ReservationAmendmentQuote>();
    service.createAmendmentQuote.mockReturnValue(pending.asObservable());

    component.requestQuote();
    component.requestQuote();

    expect(service.createAmendmentQuote).toHaveBeenCalledTimes(1);
    pending.next(quoteResponse());
    pending.complete();
  });

  it('invalidates an existing quote when the requested stay changes', () => {
    component.requestQuote();

    component.form.patchValue({ proposedAdults: 3 });
    fixture.detectChanges();

    expect(component.quote).toBeNull();
    expect(fixture.nativeElement.querySelector('.quote-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('form .amendment__primary')).not.toBeNull();
  });

  it('prevents duplicate positive-delta payment attempts while one request is unresolved', () => {
    const pending = new Subject<ReservationAmendmentQuote>();
    service.createAmendmentQuote.mockReturnValue(of(positiveQuoteResponse()));
    service.createAmendmentPaymentAttempt.mockReturnValue(pending.asObservable());
    component.requestQuote();

    component.confirm();
    component.confirm();

    expect(service.createAmendmentPaymentAttempt).toHaveBeenCalledTimes(1);
    pending.next(positiveQuoteResponse());
    pending.complete();
  });

  it('applies a decrease directly and emits completion once', () => {
    const applied = vi.fn();
    component.applied.subscribe(applied);
    component.requestQuote();
    component.confirm();

    expect(service.applyAmendmentQuote).toHaveBeenCalledTimes(1);
    expect(applied).toHaveBeenCalledWith(42);
  });

  function contextResponse() {
    return {
      reservationId: 42,
      allowed: true,
      cutoffAt: '2026-08-10T06:55:00Z',
      policyVersion: 'RESERVATION_CHANGE_POLICY_V1',
      current: snapshot(1000000, 300000),
      roomTypeOptions: [{ id: 7, name: 'Deluxe', maxAdults: 4, maxChildren: 2, maxGuests: 5 }],
      paymentMethods: ['SIMULATOR'],
    };
  }

  function quoteResponse(): ReservationAmendmentQuote {
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

  function positiveQuoteResponse(): ReservationAmendmentQuote {
    return {
      ...quoteResponse(),
      status: 'AWAITING_PAYMENT',
      proposed: snapshot(1200000, 360000),
      priceDelta: { amount: 200000, currency: 'VND' },
      settlement: {
        type: 'PAYMENT_REQUIRED',
        amount: { amount: 200000, currency: 'VND' },
        paymentAttempt: null,
        refundRequestPublicId: null,
      },
    };
  }

  function snapshot(total: number, deposit: number) {
    return {
      roomTypeId: 7,
      roomTypeName: 'Deluxe',
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      quantity: 1,
      adults: 2,
      children: 0,
      totalAmount: { amount: total, currency: 'VND' as const },
      depositRequired: { amount: deposit, currency: 'VND' as const },
    };
  }
});
