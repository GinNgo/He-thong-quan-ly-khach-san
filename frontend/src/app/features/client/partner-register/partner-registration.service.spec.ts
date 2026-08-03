import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { PartnerRegistrationService } from './partner-registration.service';

describe('PartnerRegistrationService', () => {
  let service: PartnerRegistrationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PartnerRegistrationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the anonymous registration contract on the public endpoint', () => {
    const payload = {
      email: 'owner@example.com',
      password: 'secret123',
      fullName: 'Partner Owner',
      phone: '0900000000',
      propertyName: 'Seaside Hotel',
      provinceId: 10,
      wardId: 11,
      address: '12 Bach Dang'
    };

    service.registerAnonymous(payload).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/partner/register`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ userId: 7, propertyId: 9, status: 'DRAFT' });
  });

  it('sends only property fields to the authenticated conversion endpoint', () => {
    const propertyPayload = {
      propertyName: 'Existing Customer Hotel',
      provinceId: 10,
      wardId: 11,
      address: '18 Nguyen Hue'
    };

    service.convertAuthenticated(propertyPayload).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/partner/convert`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(propertyPayload);
    expect(Object.keys(request.request.body).sort()).toEqual([
      'address',
      'propertyName',
      'provinceId',
      'wardId'
    ]);
    request.flush({ userId: 7, propertyId: 9, status: 'DRAFT' });
  });
});
