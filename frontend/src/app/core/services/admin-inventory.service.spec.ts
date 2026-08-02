import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { AdminInventoryService } from './admin-inventory.service';

describe('AdminInventoryService', () => {
  let service: AdminInventoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminInventoryService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminInventoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses dedicated start and complete maintenance commands', () => {
    service.startRoomMaintenance(12).subscribe();
    const start = http.expectOne(`${environment.apiUrl}/rooms/12/maintenance/start`);
    expect(start.request.method).toBe('POST');
    start.flush({});

    service.completeRoomMaintenance(12).subscribe();
    const complete = http.expectOne(`${environment.apiUrl}/rooms/12/maintenance/complete`);
    expect(complete.request.method).toBe('POST');
    complete.flush({});
  });
});
