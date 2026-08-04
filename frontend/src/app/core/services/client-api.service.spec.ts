import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ClientApiService, LocationSuggestion } from './client-api.service';

describe('ClientApiService popular destinations cache', () => {
  let service: ClientApiService;
  let http: HttpTestingController;
  let now: number;

  const destinations: LocationSuggestion[] = [
    {
      type: 'PROVINCE',
      id: 1,
      name: 'Province One',
      displayName: 'Province One',
      propertyCount: 4,
    },
  ];

  beforeEach(() => {
    now = 1_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    TestBed.configureTestingModule({
      providers: [ClientApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClientApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('deduplicates concurrent subscribers for the same clamped limit', () => {
    const first = vi.fn();
    const second = vi.fn();

    service.getPopularDestinations(99).subscribe(first);
    service.getPopularDestinations(8).subscribe(second);

    const request = expectPopularRequest(8);
    request.flush(destinations);

    expect(first).toHaveBeenCalledWith(destinations);
    expect(second).toHaveBeenCalledWith(destinations);
  });

  it('serves a cached response within 60 seconds and refreshes it at expiry', () => {
    const initial = vi.fn();
    service.getPopularDestinations(6).subscribe(initial);
    expectPopularRequest(6).flush(destinations);

    now = 60_999;
    const cached = vi.fn();
    service.getPopularDestinations(6).subscribe(cached);
    http.expectNone(request => request.url === `${environment.apiUrl}/public/popular-destinations`);
    expect(cached).toHaveBeenCalledWith(destinations);

    now = 61_000;
    const refreshed = vi.fn();
    service.getPopularDestinations(6).subscribe(refreshed);
    expectPopularRequest(6).flush([]);
    expect(refreshed).toHaveBeenCalledWith([]);
  });

  it('bypasses a fresh cache entry when force refresh is requested', () => {
    service.getPopularDestinations(8).subscribe();
    expectPopularRequest(8).flush(destinations);

    const cached = vi.fn();
    service.getPopularDestinations(8).subscribe(cached);
    http.expectNone(request => request.url === `${environment.apiUrl}/public/popular-destinations`);
    expect(cached).toHaveBeenCalledWith(destinations);

    const forced = vi.fn();
    service.getPopularDestinations(8, true).subscribe(forced);
    expectPopularRequest(8).flush([]);
    expect(forced).toHaveBeenCalledWith([]);
  });

  it('evicts a failed request so retry creates a new HTTP call', () => {
    const failure = vi.fn();
    service.getPopularDestinations(5).subscribe({ error: failure });
    expectPopularRequest(5).flush(
      { message: 'temporary failure' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(failure).toHaveBeenCalledOnce();

    const retry = vi.fn();
    service.getPopularDestinations(5).subscribe(retry);
    expectPopularRequest(5).flush(destinations);
    expect(retry).toHaveBeenCalledWith(destinations);
  });

  it('does not let an older failed request evict a newer forced refresh', () => {
    const staleFailure = vi.fn();
    service.getPopularDestinations(8).subscribe({ error: staleFailure });
    const staleRequest = expectPopularRequest(8);

    const refreshedDestinations = [{ ...destinations[0], propertyCount: 9 }];
    const forced = vi.fn();
    service.getPopularDestinations(8, true).subscribe(forced);
    const forcedRequest = expectPopularRequest(8);
    forcedRequest.flush(refreshedDestinations);
    staleRequest.flush(
      { message: 'late stale failure' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(staleFailure).toHaveBeenCalledOnce();
    expect(forced).toHaveBeenCalledWith(refreshedDestinations);

    const cached = vi.fn();
    service.getPopularDestinations(8).subscribe(cached);
    http.expectNone(request => request.url === `${environment.apiUrl}/public/popular-destinations`);
    expect(cached).toHaveBeenCalledWith(refreshedDestinations);
  });

  function expectPopularRequest(limit: number) {
    const request = http.expectOne(
      candidate => candidate.url === `${environment.apiUrl}/public/popular-destinations`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('limit')).toBe(String(limit));
    return request;
  }
});
