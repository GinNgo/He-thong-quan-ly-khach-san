import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { SubscriptionService } from './subscription.service';

describe('SubscriptionService', () => {
  let service: SubscriptionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SubscriptionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('scopes every account subscription read to the selected property', () => {
    service.getPlans().subscribe();
    expect(http.expectOne(`${environment.apiUrl}/subscriptions/plans`).request.method).toBe('GET');

    service.getPropertySubscription(17).subscribe();
    service.getPropertyFeatures(17).subscribe();
    service.getPropertyUsage(17).subscribe();
    for (const path of ['/me', '/me/features', '/me/usage']) {
      const request = http.expectOne(req => req.url === `${environment.apiUrl}/subscriptions${path}`);
      expect(request.request.params.get('targetHotelId')).toBe('17');
    }
    http.match(() => true).forEach(request => request.flush({}));
  });

  it('rejects subscription reads without a valid selected property', () => {
    expect(() => service.getPropertySubscription(0)).toThrowError(/selected property/);
    expect(() => service.getPropertyFeatures(Number.NaN)).toThrowError(/selected property/);
  });
});
