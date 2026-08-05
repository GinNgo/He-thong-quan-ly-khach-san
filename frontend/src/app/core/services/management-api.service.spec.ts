import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ManagementApiService } from './management-api.service';

describe('ManagementApiService property lifecycle', () => {
  let service: ManagementApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ManagementApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends owner edits to the scoped management endpoint with a reason', () => {
    service.updateProperty(19, {
      profile: {
        nameVi: 'Owner property', propertyType: 'HOTEL', addressLine: '19 Safe Street',
        provinceId: 1, wardId: 2, latitude: 10.5, longitude: 106.7,
        website: 'https://example.com', checkinTime: '14:00', checkoutTime: '12:00'
      },
      reason: 'Correct profile data'
    }).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/management/properties/19`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      profile: {
        nameVi: 'Owner property', propertyType: 'HOTEL', addressLine: '19 Safe Street',
        provinceId: 1, wardId: 2, latitude: 10.5, longitude: 106.7,
        website: 'https://example.com', checkinTime: '14:00', checkoutTime: '12:00'
      },
      reason: 'Correct profile data'
    });
    request.flush({ id: 19 });
  });

  it('reads the same canonical profile shape used by updates', () => {
    service.property(19).subscribe(profile => expect(profile.website).toBe('https://example.com'));
    http.expectOne(`${environment.apiUrl}/management/properties/19`).flush({
      id: 19, nameVi: 'Owner property', propertyType: 'HOTEL', addressLine: '19 Safe Street',
      provinceId: 1, wardId: 2, website: 'https://example.com'
    });
  });

  it('loads province and ward options for profile editing', () => {
    service.provinces().subscribe();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([]);
    service.wards(1).subscribe();
    http.expectOne(`${environment.apiUrl}/public/locations/provinces/1/wards`).flush([]);
  });
});
