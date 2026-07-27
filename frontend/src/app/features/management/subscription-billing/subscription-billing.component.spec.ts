import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { SubscriptionBillingComponent } from './subscription-billing.component';

describe('SubscriptionBillingComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubscriptionBillingComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows plans but does not offer a fake purchase action', async () => {
    const fixture = TestBed.createComponent(SubscriptionBillingComponent);
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/subscriptions/plans`).flush([
      { id: 1, code: 'STANDARD', name: 'Standard', description: 'Gói vận hành', price: 100000, currency: 'VND', durationDays: 30 },
    ]);
    http.expectOne(`${environment.apiUrl}/subscriptions/me`).flush([]);
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Standard');
    expect(element.textContent).toContain('Thanh toán online chưa hỗ trợ');
    expect(element.textContent).not.toContain('Mua ngay');
  });
});
