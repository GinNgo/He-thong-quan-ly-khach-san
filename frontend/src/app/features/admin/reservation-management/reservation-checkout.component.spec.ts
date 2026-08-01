import { registerLocaleData } from '@angular/common';
import localeVi from '@angular/common/locales/vi';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import {
  CheckoutPreview,
  CheckoutResult,
  PropertyCheckoutService,
} from '../../../core/services/property-checkout.service';
import { ReservationCheckoutComponent } from './reservation-checkout.component';

registerLocaleData(localeVi);

describe('ReservationCheckoutComponent', () => {
  let component: ReservationCheckoutComponent;
  let fixture: ComponentFixture<ReservationCheckoutComponent>;
  let checkoutService: {
    preview: ReturnType<typeof vi.fn>;
    addServiceCharge: ReturnType<typeof vi.fn>;
    addSurcharge: ReturnType<typeof vi.fn>;
    addNegativeAdjustment: ReturnType<typeof vi.fn>;
    authorizeDebtOverride: ReturnType<typeof vi.fn>;
    checkout: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    checkoutService = {
      preview: vi.fn(),
      addServiceCharge: vi.fn(),
      addSurcharge: vi.fn(),
      addNegativeAdjustment: vi.fn(),
      authorizeDebtOverride: vi.fn(),
      checkout: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ReservationCheckoutComponent],
      providers: [{ provide: PropertyCheckoutService, useValue: checkoutService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReservationCheckoutComponent);
    component = fixture.componentInstance;
    component.reservationId = 42;
  });

  it('allows checkout only when the authoritative folio is settled', () => {
    component.preview.set(makePreview('SETTLED', 0, true));
    fixture.detectChanges();

    expect(component.canCheckout()).toBe(true);
    expect(component.needsDebtOverride()).toBe(false);
    expect(component.isOverpaid()).toBe(false);
    expect(fixture.nativeElement.querySelector('.settlement-chip.settled')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.override-panel')).toBeNull();
    expect(fixture.nativeElement.querySelector('.blocking-message')).toBeNull();
  });

  it('blocks an outstanding folio until a server-issued debt override is authorized', () => {
    const outstanding = makePreview('OUTSTANDING', 250_000, false);
    component.preview.set(outstanding);
    checkoutService.authorizeDebtOverride.mockReturnValue(of({
      overrideId: 77,
      debtOverrideApplied: true,
      preview: outstanding,
    }));
    component.overrideForm.setValue({ reason: 'Approved corporate debt account' });
    fixture.detectChanges();

    expect(component.canCheckout()).toBe(false);
    expect(component.needsDebtOverride()).toBe(true);
    expect(fixture.nativeElement.querySelector('.override-panel')).not.toBeNull();

    component.authorizeDebtOverride();
    fixture.detectChanges();

    expect(checkoutService.authorizeDebtOverride).toHaveBeenCalledWith(
      42,
      'Approved corporate debt account',
    );
    expect(component.checkoutOverrideId()).toBe(77);
    expect(component.canCheckout()).toBe(true);
    expect(component.needsDebtOverride()).toBe(false);
  });

  it('keeps an overpaid folio blocked and exposes the resolution warning', () => {
    component.preview.set(makePreview('OVERPAID', -100_000, false));
    fixture.detectChanges();

    expect(component.isOverpaid()).toBe(true);
    expect(component.canCheckout()).toBe(false);
    expect(component.needsDebtOverride()).toBe(false);
    expect(fixture.nativeElement.querySelector('.blocking-message')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.override-panel')).toBeNull();
  });

  it('emits the finalized invoice result after atomic checkout succeeds', () => {
    const result = makeCheckoutResult();
    const completed = vi.fn();
    component.preview.set(makePreview('SETTLED', 0, true));
    component.completed.subscribe(completed);
    checkoutService.checkout.mockReturnValue(of(result));

    component.checkout();

    expect(checkoutService.checkout).toHaveBeenCalledWith(42, undefined);
    expect(completed).toHaveBeenCalledWith(result);
    expect(component.successMessage()).toContain('INV-2026-0042');
    expect(component.busyAction()).toBeNull();
  });

  it('passes only the approved override identifier into checkout', () => {
    const result = makeCheckoutResult();
    component.preview.set(makePreview('OUTSTANDING', 250_000, false));
    component.checkoutOverrideId.set(77);
    checkoutService.checkout.mockReturnValue(of(result));

    component.checkout();

    expect(checkoutService.checkout).toHaveBeenCalledWith(42, 77);
  });
});

function makePreview(
  settlementState: CheckoutPreview['settlementState'],
  balance: number,
  checkoutAllowed: boolean,
): CheckoutPreview {
  return {
    reservationId: 42,
    hotelId: 9,
    settlementState,
    checkoutAllowed,
    blockingError: checkoutAllowed ? null : settlementState === 'OUTSTANDING'
      ? 'OUTSTANDING_BALANCE'
      : 'OVERPAYMENT_REQUIRES_RESOLUTION',
    sourceVersion: 5,
    calculatedAt: '2026-08-01T09:30:00Z',
    folio: {
      roomCharges: 1_000_000,
      serviceCharges: 150_000,
      surchargeCharges: 50_000,
      taxCharges: 0,
      feeCharges: 0,
      discounts: 0,
      grossCharges: 1_200_000,
      depositRequired: 300_000,
      successfulPayments: 1_200_000 - balance,
      successfulRefunds: 0,
      otherCredits: 0,
      netSettled: 1_200_000 - balance,
      balance,
      lines: [{
        sourceType: 'RESERVATION',
        sourceId: 42,
        category: 'ROOM',
        code: 'ROOM-DELUXE',
        name: 'Deluxe room',
        description: null,
        quantity: 1,
        unitPrice: 1_000_000,
        taxAmount: 0,
        discountAmount: 0,
        snapshotAmount: 1_000_000,
        signedEffect: 1_000_000,
        usageStartedAt: '2026-07-31T14:00:00Z',
        usageEndedAt: '2026-08-01T09:00:00Z',
      }],
      sourceVersion: 5,
      calculatedAt: '2026-08-01T09:30:00Z',
    },
  };
}

function makeCheckoutResult(): CheckoutResult {
  return {
    reservationId: 42,
    reservationStatus: 'CHECKED_OUT',
    invoiceId: 420,
    invoiceNumber: 'INV-2026-0042',
    invoiceStatus: 'FINALIZED',
    totalAmount: 1_200_000,
    dirtyRoomIds: [12],
    financialSummary: {
      grossCharges: 1_200_000,
      depositRequired: 300_000,
      successfulPayments: 1_200_000,
      successfulRefunds: 0,
      remainingBalance: 0,
      financialState: 'SETTLED',
      sourceVersion: 5,
      calculatedAt: '2026-08-01T09:30:00Z',
    },
  };
}
