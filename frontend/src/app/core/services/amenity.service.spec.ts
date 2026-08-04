import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AmenityService } from './amenity.service';

describe('AmenityService', () => {
  let service: AmenityService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AmenityService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses public catalog and tenant-scoped assignment contracts', () => {
    service.publicCatalog().subscribe();
    http.expectOne(`${environment.apiUrl}/public/amenities`).flush([]);

    service.assignments('property', 7).subscribe();
    http.expectOne(`${environment.apiUrl}/v1/properties/7/amenities`).flush([]);

    service.replaceAssignments('roomType', 9, [1, 3]).subscribe();
    const replace = http.expectOne({ method: 'PUT', url: `${environment.apiUrl}/v1/room-types/9/amenities` });
    expect(replace.request.body).toEqual({ amenityIds: [1, 3] });
    replace.flush([]);
  });

  it('uses the system catalog lifecycle endpoints', () => {
    service.createCatalogEntry({ code: 'SPA', nameVi: 'Spa', category: 'WELLNESS', sortOrder: 10 }).subscribe();
    http.expectOne({ method: 'POST', url: `${environment.apiUrl}/admin/amenities` }).flush({});
    service.deactivateCatalogEntry(12).subscribe();
    http.expectOne({ method: 'POST', url: `${environment.apiUrl}/admin/amenities/12/deactivate` }).flush({});
  });
});
