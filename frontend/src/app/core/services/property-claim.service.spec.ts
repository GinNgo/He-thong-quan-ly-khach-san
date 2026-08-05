import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import {
  PropertyClaimService,
  propertyClaimRequestErrorMessage
} from './property-claim.service';

describe('PropertyClaimService', () => {
  let service: PropertyClaimService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PropertyClaimService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('submits a typed requester payload to the property claim endpoint', () => {
    const payload = {
      verificationMethod: 'EMAIL' as const,
      verificationData: 'owner@example.com',
      note: 'Company mailbox'
    };

    service.submit(17, payload).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/properties/17/claim`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 81, status: 'PENDING' });
  });

  it('loads a typed paged admin queue', () => {
    service.list('PENDING', 2, 25).subscribe();

    const request = http.expectOne(req =>
      req.url === `${environment.apiUrl}/admin/property-claims`
      && req.params.get('status') === 'PENDING'
      && req.params.get('page') === '2'
      && req.params.get('size') === '25'
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 25 });
  });

  it('maps duplicate and rate-limit failures without exposing backend detail', () => {
    const invalid = new HttpErrorResponse({
      status: 400,
      error: { message: 'Constraint implementation details' }
    });
    const conflict = new HttpErrorResponse({
      status: 409,
      error: { message: 'Internal duplicate key details' }
    });
    const concurrentConflict = new HttpErrorResponse({
      status: 409,
      error: { code: 'PROPERTY_CLAIM_CONFLICT', message: 'Filtered index details' }
    });
    const throttled = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '75' }),
      error: { message: 'Internal limiter state' }
    });

    expect(propertyClaimRequestErrorMessage(invalid)).toContain('chưa hợp lệ');
    expect(propertyClaimRequestErrorMessage(invalid)).not.toContain('Constraint');
    expect(propertyClaimRequestErrorMessage(conflict)).toContain('đang được xử lý');
    expect(propertyClaimRequestErrorMessage(conflict)).not.toContain('Internal');
    expect(propertyClaimRequestErrorMessage(concurrentConflict)).toContain('đồng thời');
    expect(propertyClaimRequestErrorMessage(concurrentConflict)).not.toContain('Filtered index');
    expect(propertyClaimRequestErrorMessage(throttled)).toContain('2 phút');
    expect(propertyClaimRequestErrorMessage(throttled)).not.toContain('Internal');
  });
});
