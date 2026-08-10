import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReservationService } from '../../../core/services/reservation.service';
import { RoomService } from '../../../core/services/room.service';
import { ReservationTimelineComponent } from './reservation-timeline.component';

describe('ReservationTimelineComponent', () => {
  it('searches the complete room inventory before applying the display limit', async () => {
    const rooms = Array.from({ length: 301 }, (_, index) => ({
      id: index + 1,
      roomNumber: index === 300 ? 'TARGET-ROOM' : `R-${index + 1}`,
      floor: 1,
      status: 'AVAILABLE',
    }));
    await TestBed.configureTestingModule({
      imports: [ReservationTimelineComponent],
      providers: [
        { provide: RoomService, useValue: { getAllRooms: () => of(rooms) } },
        { provide: ReservationService, useValue: { getAllReservations: () => of([]) } },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(ReservationTimelineComponent).componentInstance;

    component.ngOnInit();
    component.roomQuery = 'target';

    expect(component.rooms).toHaveLength(301);
    expect(component.filteredRooms.map((room) => room.roomNumber)).toEqual(['TARGET-ROOM']);
  });

  it('uses an inclusive custom date range and caps it at 31 days', async () => {
    await TestBed.configureTestingModule({
      imports: [ReservationTimelineComponent],
      providers: [
        { provide: RoomService, useValue: { getAllRooms: () => of([]) } },
        { provide: ReservationService, useValue: { getAllReservations: () => of([]) } },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(ReservationTimelineComponent).componentInstance;
    component.startDate = new Date(2026, 7, 1);
    component.endDate = new Date(2026, 8, 20);

    component.onEndDateChange();

    expect(component.visibleDays).toBe(31);
    expect(component.dates).toHaveLength(31);
    expect(component.endDate).toEqual(new Date(2026, 7, 31));
  });
});
