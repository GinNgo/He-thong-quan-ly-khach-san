import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { PaymentService, PaymentSessionStatus } from '../../../core/services/payment.service';
import { PaymentResultComponent } from './payment-result';

describe('PaymentResult', () => {
  let component: PaymentResultComponent;
  let fixture: ComponentFixture<PaymentResultComponent>;

  const succeeded: PaymentSessionStatus = {
    sessionId: 'session-1',
    reservationId: 42,
    provider: 'VNPAY',
    amount: 350000,
    currency: 'VND',
    status: 'SUCCEEDED',
    expiresAt: '2026-07-29T12:00:00',
    reconciliationRequired: false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentResultComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: PaymentService, useValue: { getPaymentSessionStatus: vi.fn(() => of(succeeded)) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentResultComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('shows success only after the authenticated server session succeeds', () => {
    applyStatus(succeeded);
    fixture.detectChanges();

    expect(component.status).toBe('SUCCESS');
    expect(component.reservationId).toBe(42);
    expect(component.message).toContain('đã xác nhận giao dịch');
  });

  it('keeps browser return pending while the authoritative callback has not arrived', () => {
    applyStatus({ ...succeeded, status: 'PENDING' });
    fixture.detectChanges();

    expect(component.status).toBe('PENDING');
    expect(component.message).toContain('callback máy chủ');
  });

  it('shows a reconciliation state instead of reviving an expired reservation', () => {
    applyStatus({ ...succeeded, reconciliationRequired: true });
    fixture.detectChanges();

    expect(component.status).toBe('RECONCILIATION');
    expect(component.message).toContain('đối soát');
  });

  function applyStatus(status: PaymentSessionStatus): void {
    (component as unknown as { applySessionStatus: (value: PaymentSessionStatus) => void })
      .applySessionStatus(status);
  }
});
