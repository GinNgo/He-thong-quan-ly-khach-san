import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { CheckInReadiness, ReservationService } from '../../core/services/reservation.service';
import { CheckInReadinessComponent } from './check-in-readiness.component';

describe('CheckInReadinessComponent', () => {
  let fixture: ComponentFixture<CheckInReadinessComponent>;
  let getCheckInReadiness: ReturnType<typeof vi.fn>;
  let checkIn: ReturnType<typeof vi.fn>;
  let language: 'vi' | 'en';

  const ready: CheckInReadiness = {
    reservationId: 55, reservationStatus: 'CONFIRMED', ready: true, alreadyCheckedIn: false,
    evaluatedAt: '2026-08-02T14:00:00+07:00', scheduledArrivalAt: '2026-08-02T14:00:00+07:00',
    earliestCheckInAt: '2026-08-02T13:55:00+07:00', latestCheckInAt: '2026-08-03T12:00:00+07:00',
    zoneId: 'Asia/Ho_Chi_Minh', earlyWindowMinutes: 5, policyVersion: 'CHECK_IN_POLICY_V1', requiredRoomCount: 1,
    assignedRooms: [{ id: 11, hotelId: 3, roomTypeId: 7, roomNumber: '101', floor: 1, status: 'RESERVED', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' }],
    blockers: [],
  };

  beforeEach(async () => {
    language = 'vi';
    getCheckInReadiness = vi.fn(() => of(ready));
    checkIn = vi.fn(() => of({ id: 55, status: 'CHECKED_IN' }));
    await TestBed.configureTestingModule({
      imports: [CheckInReadinessComponent],
      providers: [
        { provide: ReservationService, useValue: { getCheckInReadiness, checkIn } },
        { provide: TranslateService, useValue: { getCurrentLang: () => language } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CheckInReadinessComponent);
    fixture.componentRef.setInput('reservationId', 55);
    fixture.componentRef.setInput('canAssignRooms', true);
    fixture.detectChanges();
  });

  it('requires authoritative readiness and explicit confirmation before posting once', () => {
    const pending = new Subject<any>();
    checkIn.mockReturnValue(pending);
    const button = fixture.nativeElement.querySelector('.primary') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fixture.componentInstance.confirmed = true;
    fixture.detectChanges();
    button.click();
    button.click();
    expect(checkIn).toHaveBeenCalledTimes(1);
    expect(checkIn).toHaveBeenCalledWith(55, expect.stringContaining('check-in-55-'));
  });

  it('offers room assignment for a missing assignment blocker', () => {
    getCheckInReadiness.mockReturnValue(of({ ...ready, ready: false, assignedRooms: [], blockers: [{ code: 'MISSING_ROOM_ASSIGNMENT', message: 'missing' }] }));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assign')).not.toBeNull();
    expect((fixture.nativeElement.querySelector('.primary') as HTMLButtonElement).disabled).toBe(true);
  });

  it('ignores stale readiness responses after a newer refresh', () => {
    const stale = new Subject<CheckInReadiness>();
    getCheckInReadiness.mockReturnValueOnce(stale).mockReturnValueOnce(of({ ...ready, requiredRoomCount: 2 }));
    fixture.componentInstance.refresh();
    fixture.componentInstance.refresh();
    stale.next({ ...ready, requiredRoomCount: 9 });
    expect(fixture.componentInstance.readiness?.requiredRoomCount).toBe(2);
  });

  it('refreshes after 409 and requires confirmation again', () => {
    checkIn.mockReturnValue(throwError(() => ({ status: 409 })));
    fixture.componentInstance.confirmed = true;
    fixture.componentInstance.confirmCheckIn();
    expect(getCheckInReadiness).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.confirmed).toBe(false);
    expect(fixture.componentInstance.error).toContain('xác nhận lần nữa');
  });

  it('renders localized English copy and already-checked-in state', () => {
    language = 'en';
    getCheckInReadiness.mockReturnValue(of({ ...ready, ready: false, alreadyCheckedIn: true }));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Check-in readiness');
    expect(fixture.nativeElement.textContent).toContain('already checked in');
  });
});
