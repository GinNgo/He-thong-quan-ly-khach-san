import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { MaintenanceWorkOrderService } from './maintenance-work-order.service';

describe('MaintenanceWorkOrderService', () => {
  let service: MaintenanceWorkOrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MaintenanceWorkOrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('scopes the work-order query to property and room', () => {
    service.getAll(7, 12).subscribe();
    const request = http.expectOne(req => req.url === `${environment.apiUrl}/v1/maintenance-work-orders`);
    expect(request.request.params.get('propertyId')).toBe('7');
    expect(request.request.params.get('roomId')).toBe('12');
    request.flush([]);
  });

  it('uses explicit lifecycle transition endpoints', () => {
    service.start(8).subscribe();
    http.expectOne(`${environment.apiUrl}/v1/maintenance-work-orders/8/start`).flush({});
    service.complete(8).subscribe();
    http.expectOne(`${environment.apiUrl}/v1/maintenance-work-orders/8/complete`).flush({});
    service.reopen(8, 'Can sua lai').subscribe();
    http.expectOne(`${environment.apiUrl}/v1/maintenance-work-orders/8/reopen`).flush({});
    service.cancel(8, 'Doi lich').subscribe();
    http.expectOne(`${environment.apiUrl}/v1/maintenance-work-orders/8/cancel`).flush({});
  });
});
