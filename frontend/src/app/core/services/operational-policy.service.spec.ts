import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { OperationalPolicyService } from './operational-policy.service';

describe('OperationalPolicyService', () => {
  let service: OperationalPolicyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OperationalPolicyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses tenant management and public effective-policy contracts', () => {
    service.list(7).subscribe();
    http.expectOne(`${environment.apiUrl}/v1/hotels/7/policies`).flush([]);

    service.current(7, 'vi', '2026-08-10').subscribe();
    const current = http.expectOne(request => request.url === `${environment.apiUrl}/v1/hotels/public/7/policies/current`);
    expect(current.request.params.get('locale')).toBe('vi');
    expect(current.request.params.get('stayDate')).toBe('2026-08-10');
    current.flush({ version: 1 });
  });
});
