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
});
