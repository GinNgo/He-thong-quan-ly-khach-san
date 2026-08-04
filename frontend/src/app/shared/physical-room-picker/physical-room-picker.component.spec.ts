import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AvailableRoomContext, ReservationService } from '@app/core/services/reservation.service';
import { PhysicalRoomPickerComponent } from './physical-room-picker.component';

describe('PhysicalRoomPickerComponent', () => {
  let fixture: ComponentFixture<PhysicalRoomPickerComponent>;
  let getAvailableRoomContext: ReturnType<typeof vi.fn>;

  const context: AvailableRoomContext = {
    reservationId: 88,
    hotelId: 3,
    roomTypeId: 7,
    roomTypeName: 'Deluxe',
    checkInDate: '2026-08-10',
    checkOutDate: '2026-08-12',
    requiredQuantity: 2,
    assignedRoomIds: [19],
    candidates: [
      { id: 11, hotelId: 3, roomTypeId: 7, roomNumber: '101', floor: 1, status: 'AVAILABLE', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' },
      { id: 12, hotelId: 3, roomTypeId: 7, roomNumber: '201', floor: 2, status: 'AVAILABLE', housekeepingStatus: 'INSPECTED', maintenanceStatus: 'NONE' },
      { id: 13, hotelId: 3, roomTypeId: 7, roomNumber: '202', floor: 2, status: 'AVAILABLE', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' },
    ],
  };

  beforeEach(async () => {
    getAvailableRoomContext = vi.fn(() => of(context));
    await TestBed.configureTestingModule({
      imports: [PhysicalRoomPickerComponent],
      providers: [{ provide: ReservationService, useValue: { getAvailableRoomContext } }],
    }).compileComponents();
    fixture = TestBed.createComponent(PhysicalRoomPickerComponent);
    fixture.componentRef.setInput('reservationId', 88);
    fixture.detectChanges();
  });

  async function clickAndRender(element: HTMLElement): Promise<void> {
    element.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads candidates and limits selection to the exact booking quantity', async () => {
    const component = fixture.componentInstance;
    const cards = fixture.nativeElement.querySelectorAll('.room-card') as NodeListOf<HTMLElement>;
    await clickAndRender(cards[0]);
    await clickAndRender(cards[1]);
    await clickAndRender(cards[2]);

    expect(getAvailableRoomContext).toHaveBeenCalledWith(88);
    expect([...component.selectedRoomIds]).toEqual([11, 12]);
    expect(component.selectionComplete).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('2/2');
  });

  it('refreshes safely, removes stale selections and shows a shortage', async () => {
    const component = fixture.componentInstance;
    const cards = fixture.nativeElement.querySelectorAll('.room-card') as NodeListOf<HTMLElement>;
    await clickAndRender(cards[0]);
    await clickAndRender(cards[1]);
    getAvailableRoomContext.mockReturnValue(of({
      ...context,
      candidates: [context.candidates[1]],
    }));

    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));

    expect([...component.selectedRoomIds]).toEqual([12]);
    expect(component.hasShortage).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Chỉ có 1 phòng phù hợp');
  });

  it('renders a retryable error state without stale candidates', async () => {
    getAvailableRoomContext.mockReturnValue(throwError(() => ({ error: { message: 'Kho phòng vừa thay đổi' } })));

    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));

    expect(fixture.componentInstance.context).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Kho phòng vừa thay đổi');
    expect(fixture.nativeElement.querySelector('.picker-state--error button')).toBeTruthy();
  });

  it('ignores a stale response after the reservation input changes', () => {
    const first = new Subject<AvailableRoomContext>();
    const second = new Subject<AvailableRoomContext>();
    getAvailableRoomContext.mockReset();
    getAvailableRoomContext.mockReturnValueOnce(first).mockReturnValueOnce(second);

    fixture.componentRef.setInput('reservationId', 90);
    fixture.detectChanges();
    fixture.componentRef.setInput('reservationId', 91);
    fixture.detectChanges();
    second.next({ ...context, reservationId: 91 });
    first.next({ ...context, reservationId: 90, roomTypeName: 'Stale room type' });
    fixture.detectChanges();

    expect(fixture.componentInstance.context?.reservationId).toBe(91);
    expect(fixture.nativeElement.textContent).not.toContain('Stale room type');
  });

  it('distinguishes an empty inventory from a partial shortage', async () => {
    getAvailableRoomContext.mockReturnValue(of({ ...context, candidates: [] }));

    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));

    expect(fixture.componentInstance.isEmpty).toBe(true);
    expect(fixture.componentInstance.hasShortage).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Không có phòng vật lý phù hợp');
  });
});
