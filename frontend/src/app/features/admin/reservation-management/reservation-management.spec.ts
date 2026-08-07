import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { Reservation, ReservationService } from '../../../core/services/reservation.service';
import { PaymentService } from '../../../core/services/payment.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { HotelServiceService } from '../../../core/services/hotel-service.service';
import { Router } from '@angular/router';
import { ReservationManagement } from './reservation-management';

describe('ReservationManagement payment and refund states', () => {
  let component: ReservationManagement;
  let fixture: ComponentFixture<ReservationManagement>;
  let reservations$: Subject<Reservation[]>;

  beforeEach(async () => {
    reservations$ = new Subject<Reservation[]>();
    await TestBed.configureTestingModule({
      imports: [ReservationManagement],
      providers: [
        {
          provide: ReservationService,
          useValue: { getAllReservations: vi.fn(() => reservations$.asObservable()) },
        },
        { provide: PaymentService, useValue: {} },
        { provide: InvoiceService, useValue: {} },
        { provide: HotelServiceService, useValue: { getServices: vi.fn(() => of([])) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders reservations received after the initial zoneless change-detection pass', () => {
    reservations$.next([
      {
        id: 42,
        userId: 7,
        username: 'fixture-customer',
        checkInDate: '2026-08-01',
        checkOutDate: '2026-08-02',
        guests: 2,
        totalAmount: 1_250_000,
        status: 'PENDING_PAYMENT',
        paymentMethod: 'VNPAY',
        details: [],
        payment: {
          provider: 'VNPAY',
          amount: 1_250_000,
          currency: 'VND',
          status: 'PENDING',
          reconciliationRequired: false,
        },
      },
    ]);

    expect(fixture.nativeElement.querySelector('[data-booking-id="42"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-payment-status="PENDING"]')).not.toBeNull();
  });

  it('presents payment failure and reconciliation as explicit labelled states', () => {
    expect(component.getPaymentLabel({
      provider: 'VNPAY', amount: 500000, currency: 'VND', status: 'FAILED', reconciliationRequired: false,
    })).toBe('Th\u1ea5t b\u1ea1i');
    expect(component.getPaymentTone({
      provider: 'VNPAY', amount: 500000, currency: 'VND', status: 'SUCCEEDED', reconciliationRequired: true,
    })).toBe('warning');
    expect(component.getPaymentIcon({
      provider: 'VNPAY', amount: 500000, currency: 'VND', status: 'EXPIRED', reconciliationRequired: false,
    })).toBe('pi pi-clock');
  });

  it('shows the latest refund lifecycle state for admin follow-up', () => {
    const reservation = {
      id: 1, userId: 2, checkInDate: '2026-08-01', checkOutDate: '2026-08-02', guests: 2,
      totalAmount: 900000, status: 'CANCELLED', paymentMethod: 'MOMO', details: [],
      refunds: [
        { publicId: 'r1', amount: 900000, currency: 'VND', provider: 'MOMO', status: 'REQUESTED', requestedAt: '2026-07-30' },
        { publicId: 'r2', amount: 900000, currency: 'VND', provider: 'MOMO', status: 'PENDING_PROVIDER', requestedAt: '2026-07-30' },
      ],
    } as Reservation;

    const refund = component.getLatestRefund(reservation);
    expect(refund?.status).toBe('PENDING_PROVIDER');
    expect(component.getRefundLabel(refund)).toBe('\u0110ang x\u1eed l\u00fd');
    expect(component.getRefundTone(refund)).toBe('warning');
  });
});
