import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AnalyticsService } from './analytics';

describe('AnalyticsService', () => {
  it('loads the authoritative system dashboard endpoint', () => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService, provideHttpClient(), provideHttpClientTesting()]
    });
    const service = TestBed.inject(AnalyticsService);
    const http = TestBed.inject(HttpTestingController);

    service.getDashboardData().subscribe();

    const request = http.expectOne('http://localhost:8080/api/analytics/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush({});
    http.verify();
  });
});
