import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { PropertyLifecycleAction, PropertyService } from './property.service';

describe('PropertyService lifecycle workflow', () => {
  let service: PropertyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PropertyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads typed property lifecycle summaries', () => {
    service.getPropertyLifecycleSummaries().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it.each([
    ['SUSPEND', 'suspend'],
    ['REACTIVATE', 'reactivate'],
    ['CLOSE', 'close']
  ] as const)('posts the %s transition with a stable idempotency identity', (action, path) => {
    const reason = 'Operational lifecycle reason.';
    const request$ = action === 'SUSPEND'
      ? service.suspendProperty(7, reason, 'lifecycle-key-7')
      : action === 'REACTIVATE'
        ? service.reactivateProperty(7, reason, 'lifecycle-key-7')
        : service.closeProperty(7, reason, 'lifecycle-key-7');

    request$.subscribe();

    const request = http.expectOne(`${environment.apiUrl}/admin/properties/7/${path}`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason });
    expect(request.request.headers.get('Idempotency-Key')).toBe('lifecycle-key-7');
    request.flush(decision(action));
  });
});

function decision(action: PropertyLifecycleAction) {
  return {
    propertyId: 7,
    action,
    changed: true,
    actorUserId: 1,
    changedAt: '2026-08-04T10:00:00Z',
    reason: 'Operational lifecycle reason.',
    status: action === 'REACTIVATE' ? 'ACTIVE' : action === 'SUSPEND' ? 'SUSPENDED' : 'CLOSED',
    approvalStatus: 'APPROVED',
    operationStatus: action === 'REACTIVATE' ? 'ACTIVE' : action === 'SUSPEND' ? 'SUSPENDED' : 'CLOSED'
  };
}
