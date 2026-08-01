import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { PlatformPaymentConfigurationComponent } from './platform-payment-configuration.component';

describe('PlatformPaymentConfigurationComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformPaymentConfigurationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders masked merchant readiness and never renders a secret reference', async () => {
    const fixture = TestBed.createComponent(PlatformPaymentConfigurationComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/platform/payment-configuration`).flush([{
      provider: 'MOMO',
      environment: 'SANDBOX',
      enabled: true,
      merchantReferenceMasked: '****7890',
      secretConfigured: true,
      bankName: null,
      bankAccountMasked: null,
      callbackUrl: 'https://api.example.test/callback',
      productionApproved: false,
      ready: true,
      blockers: [],
    }]);
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('****7890');
    expect(element.textContent).toContain('Configured (masked)');
    expect(element.textContent).not.toContain('env:PLATFORM_MOMO');
  });
});
