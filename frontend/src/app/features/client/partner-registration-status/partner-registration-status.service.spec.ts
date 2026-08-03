import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { PartnerRegistrationStatusService } from './partner-registration-status.service';

describe('PartnerRegistrationStatusService', () => {
  let service: PartnerRegistrationStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PartnerRegistrationStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the typed per-property status contract', () => {
    let overallStatus = '';
    service.load().subscribe(response => overallStatus = response.overallStatus);

    const request = http.expectOne(`${environment.apiUrl}/partner/registration-status`);
    expect(request.request.method).toBe('GET');
    request.flush({
      overallStatus: 'PENDING',
      propertyCount: 1,
      properties: [{
        propertyId: 7,
        propertyName: 'Harbor Hotel',
        status: 'PENDING',
        approvalStatus: 'PENDING_APPROVAL',
        operationStatus: 'INACTIVE',
        ownershipStatus: 'PENDING',
        rejectionReason: null
      }]
    });

    expect(overallStatus).toBe('PENDING');
  });

  it('submits a property for review with an empty command body', () => {
    let submittedStatus = '';
    service.submitForReview(17).subscribe(response => submittedStatus = response.approvalStatus);

    const request = http.expectOne(`${environment.apiUrl}/partner/properties/17/submit`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      propertyId: 17,
      status: 'PENDING_APPROVAL',
      approvalStatus: 'PENDING_APPROVAL',
      operationStatus: 'INACTIVE',
      submittedByUserId: 42,
      submittedAt: '2026-08-04T08:30:00Z'
    });

    expect(submittedStatus).toBe('PENDING_APPROVAL');
  });
});
