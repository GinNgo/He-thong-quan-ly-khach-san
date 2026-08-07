import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { PaymentService } from '../../../core/services/payment.service';
import { PaymentSimulatorComponent } from './payment-simulator';

describe('PaymentSimulator', () => {
  let component: PaymentSimulatorComponent;
  let fixture: ComponentFixture<PaymentSimulatorComponent>;
  let confirmation$: Subject<{ status: string; message: string }>;
  let paymentService: { confirmDemoPayment: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    confirmation$ = new Subject();
    paymentService = { confirmDemoPayment: vi.fn(() => confirmation$) };

    await TestBed.configureTestingModule({
      imports: [PaymentSimulatorComponent],
      providers: [
        provideRouter([]),
        { provide: PaymentService, useValue: paymentService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSimulatorComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows an explicit recovery state when the signed token is missing', () => {
    fixture.detectChanges();

    expect(component.hasValidContext).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Phiên thanh toán không hợp lệ');
    component.confirmPayment();
    expect(paymentService.confirmDemoPayment).not.toHaveBeenCalled();
  });

  it('submits only the signed token and ignores duplicate confirmation while pending', () => {
    component.token = 'signed-token';
    component.contextError = '';

    component.confirmPayment();
    component.confirmPayment();

    expect(paymentService.confirmDemoPayment).toHaveBeenCalledTimes(1);
    expect(paymentService.confirmDemoPayment).toHaveBeenCalledWith('signed-token');
    confirmation$.next({ status: 'APPLIED', message: 'ok' });
    confirmation$.complete();
    expect(component.isSuccess).toBe(true);
  });
});
