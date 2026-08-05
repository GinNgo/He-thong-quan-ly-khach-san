import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PropertyService } from './property.service';

describe('PropertyService lifecycle', () => {
  let service: PropertyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PropertyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('closes a property through the retention endpoint with an audit reason', () => {
    service.closeProperty(8, 'Property permanently retired').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/v1/hotels/8/close`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Property permanently retired' });
    request.flush({ id: 8, status: 'CLOSED', operationStatus: 'CLOSED' });
  });
});
