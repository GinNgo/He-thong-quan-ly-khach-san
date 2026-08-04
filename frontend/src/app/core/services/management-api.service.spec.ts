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
      nameVi: 'Owner property', addressLine: '19 Safe Street', reason: 'Correct profile data'
    }).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/management/properties/19`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      nameVi: 'Owner property', addressLine: '19 Safe Street', reason: 'Correct profile data'
    });
    request.flush({ id: 19 });
  });
});
