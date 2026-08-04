import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ReservationService } from './reservation.service';

describe('ReservationService lifecycle commands', () => {
  let service: ReservationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReservationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses dedicated endpoints for check-in, operational cancellation and no-show', () => {
    service.checkIn(41).subscribe();
    const checkIn = http.expectOne(`${environment.apiUrl}/reservations/41/check-in`);
    expect(checkIn.request.method).toBe('POST');
    expect(checkIn.request.body).toEqual({});
    checkIn.flush({ id: 41 });

    service.cancelOperational(42).subscribe();
    const cancel = http.expectOne(`${environment.apiUrl}/reservations/42/cancel-operational`);
    expect(cancel.request.method).toBe('POST');
    cancel.flush({ id: 42 });

    service.markNoShow(43).subscribe();
    const noShow = http.expectOne(`${environment.apiUrl}/reservations/43/no-show`);
    expect(noShow.request.method).toBe('POST');
    noShow.flush({ id: 43 });
  });

  it('does not expose the retired legacy service-charge mutation', () => {
    expect('addExtraService' in service).toBe(false);
  });

  it('loads a typed physical-room picker context for a reservation', () => {
    service.getAvailableRoomContext(88).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/reservations/88/available-rooms/context`);
    expect(request.request.method).toBe('GET');
    request.flush({
      reservationId: 88,
      hotelId: 3,
      roomTypeId: 7,
      roomTypeName: 'Deluxe',
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      requiredQuantity: 2,
      assignedRooms: [],
      assignedRoomIds: [],
      candidates: [],
    });
  });

  it('sends idempotent assignment and release commands', () => {
    service.updateRoomAssignment(88, {
      roomIds: [11, 12],
      reason: 'Phân phòng gần thang máy',
    }, 'assignment-key').subscribe();

    const assignment = http.expectOne(`${environment.apiUrl}/reservations/88/room-assignment`);
    expect(assignment.request.method).toBe('POST');
    expect(assignment.request.headers.get('Idempotency-Key')).toBe('assignment-key');
    expect(assignment.request.body).toEqual({ roomIds: [11, 12], reason: 'Phân phòng gần thang máy' });
    assignment.flush({ id: 88 });

    service.releaseRoomAssignment(88, 'Giải phóng để bảo trì', 'release-key').subscribe();
    const release = http.expectOne(`${environment.apiUrl}/reservations/88/room-assignment/release`);
    expect(release.request.method).toBe('POST');
    expect(release.request.headers.get('Idempotency-Key')).toBe('release-key');
    expect(release.request.body).toEqual({ reason: 'Giải phóng để bảo trì' });
    release.flush({ id: 88 });
  });
});
