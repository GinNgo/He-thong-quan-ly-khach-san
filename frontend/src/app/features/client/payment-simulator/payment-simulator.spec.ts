import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PaymentSimulatorComponent } from './payment-simulator';
import { provideRouter } from '@angular/router';

describe('PaymentSimulator', () => {
  let component: PaymentSimulatorComponent;
  let fixture: ComponentFixture<PaymentSimulatorComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentSimulatorComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSimulatorComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows an explicit recovery state when payment context is missing', () => {
    fixture.detectChanges();

    expect(component.hasValidContext).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Phiên thanh toán không hợp lệ');
    component.confirmPayment();
    http.verify();
  });

  it('ignores duplicate confirmation while the callback is pending', () => {
    component.reservationId = 1;
    component.method = 'MOMO';
    component.amount = 100000;
    component.transactionId = 'TX-1';
    component.contextError = '';

    component.confirmPayment();
    component.confirmPayment();

    const request = http.expectOne(request => request.url.includes('/payments/callback'));
    expect(request.request.method).toBe('GET');
    request.flush({ message: 'ok' });
    expect(component.isSuccess).toBe(true);
    http.verify();
  });
});
