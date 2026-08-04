import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { PropertyService } from './property.service';

describe('PropertyService approval workflow', () => {
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

  it('loads the typed approval queue', () => {
    service.getPropertyApprovalQueue().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('posts an empty approve command to the admin workflow', () => {
    service.approvePropertyReview(7).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/approve`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(decision('APPROVED'));
  });

  it('posts only the validated rejection reason', () => {
    service.rejectPropertyReview(7, 'Thiếu giấy phép kinh doanh hợp lệ.').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/reject`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Thiếu giấy phép kinh doanh hợp lệ.' });
    request.flush(decision('REJECTED'));
  });
});

function decision(outcome: 'APPROVED' | 'REJECTED') {
  return {
    propertyId: 7,
    status: outcome === 'APPROVED' ? 'ACTIVE' : 'REJECTED',
    approvalStatus: outcome,
    operationStatus: outcome === 'APPROVED' ? 'ACTIVE' : 'INACTIVE',
    ownershipStatus: outcome === 'APPROVED' ? 'ACTIVE' : 'INACTIVE',
    reviewedByUserId: 1,
    reviewedAt: '2026-08-04T10:00:00Z',
    reason: outcome === 'REJECTED' ? 'Thiếu giấy phép kinh doanh hợp lệ.' : null
  };
}
