import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { SubscriptionBillingComponent } from './subscription-billing.component';

describe('SubscriptionBillingComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubscriptionBillingComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows backend catalog plans and truthful policy blockers', async () => {
    const fixture = TestBed.createComponent(SubscriptionBillingComponent);
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/platform/subscription-plans`).flush([
      {
        id: 1,
        code: 'STANDARD',
        nameVi: 'Standard',
        nameEn: 'Standard',
        billingType: 'MONTHLY',
        price: 100000,
        currency: 'VND',
        isLifetime: false,
        status: 'ACTIVE',
        features: [{ code: 'MAX_PROPERTIES', nameVi: 'Properties', nameEn: 'Properties', valueType: 'NUMERIC', limit: 3 }],
      },
    ]);
    http.expectOne(`${environment.apiUrl}/subscriptions/me`).flush([]);
    http.expectOne(`${environment.apiUrl}/subscriptions/me/usage`).flush({
      planCode: 'NO_PLAN', subscriptionStatus: 'NONE', lifetime: false, limits: {}, usage: {}, features: [],
    });
    http.expectOne(`${environment.apiUrl}/platform/subscription-policies`).flush({
      downgradeConfigured: false,
      prorationConfigured: false,
      errorCode: 'POLICY_NOT_CONFIGURED',
      downgradeMessage: 'Downgrade is blocked',
      prorationMessage: 'Proration is blocked',
    });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Standard');
    expect(element.textContent).toContain('Create secure order');
    expect(element.textContent).toContain('Downgrade is blocked');
  });
});
