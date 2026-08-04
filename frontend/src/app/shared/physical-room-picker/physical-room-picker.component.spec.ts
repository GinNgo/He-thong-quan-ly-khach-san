import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AvailableRoomContext, ReservationService } from '@app/core/services/reservation.service';
import { PhysicalRoomPickerComponent } from './physical-room-picker.component';

describe('PhysicalRoomPickerComponent', () => {
  let fixture: ComponentFixture<PhysicalRoomPickerComponent>;
  let getAvailableRoomContext: ReturnType<typeof vi.fn>;
  let updateRoomAssignment: ReturnType<typeof vi.fn>;
  let releaseRoomAssignment: ReturnType<typeof vi.fn>;

  const context: AvailableRoomContext = {
    reservationId: 88,
    hotelId: 3,
    roomTypeId: 7,
    roomTypeName: 'Deluxe',
    checkInDate: '2026-08-10',
    checkOutDate: '2026-08-12',
    requiredQuantity: 2,
    assignedRooms: [],
    assignedRoomIds: [],
    candidates: [
      { id: 11, hotelId: 3, roomTypeId: 7, roomNumber: '101', floor: 1, status: 'AVAILABLE', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' },
      { id: 12, hotelId: 3, roomTypeId: 7, roomNumber: '201', floor: 2, status: 'AVAILABLE', housekeepingStatus: 'INSPECTED', maintenanceStatus: 'NONE' },
      { id: 13, hotelId: 3, roomTypeId: 7, roomNumber: '202', floor: 2, status: 'AVAILABLE', housekeepingStatus: 'CLEAN', maintenanceStatus: 'NONE' },
    ],
  };

  beforeEach(async () => {
    getAvailableRoomContext = vi.fn(() => of(context));
    updateRoomAssignment = vi.fn(() => of({ id: 88 }));
    releaseRoomAssignment = vi.fn(() => of({ id: 88 }));
    await TestBed.configureTestingModule({
      imports: [PhysicalRoomPickerComponent],
      providers: [{
        provide: ReservationService,
        useValue: { getAvailableRoomContext, updateRoomAssignment, releaseRoomAssignment },
      }],
    }).compileComponents();
    fixture = TestBed.createComponent(PhysicalRoomPickerComponent);
    fixture.componentRef.setInput('reservationId', 88);
    fixture.componentRef.setInput('allowMutation', true);
    fixture.detectChanges();
  });

  async function clickAndRender(element: HTMLElement): Promise<void> {
    element.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function enterReason(value: string): Promise<void> {
    const textarea = fixture.nativeElement.querySelector('#assignment-reason') as HTMLTextAreaElement;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
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
    expect(fixture.nativeElement.textContent).toContain('Chỉ có 1 phòng có thể giữ hoặc chọn');
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
    getAvailableRoomContext.mockReturnValue(of({ ...context, assignedRooms: [], assignedRoomIds: [], candidates: [] }));

    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));

    expect(fixture.componentInstance.isEmpty).toBe(true);
    expect(fixture.componentInstance.hasShortage).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Không có phòng vật lý phù hợp');
  });

  it('preselects current rooms and submits an atomic reassignment with a stable key', async () => {
    const assignedContext: AvailableRoomContext = {
      ...context,
      assignedRooms: [
        { ...context.candidates[0], status: 'RESERVED' },
        { ...context.candidates[1], status: 'RESERVED' },
      ],
      assignedRoomIds: [11, 12],
      candidates: [context.candidates[2]],
    };
    getAvailableRoomContext.mockReturnValue(of(assignedContext));
    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));

    const cards = fixture.nativeElement.querySelectorAll('.room-card') as NodeListOf<HTMLElement>;
    await clickAndRender(cards[0]);
    await clickAndRender(cards[2]);
    await enterReason('Đổi phòng do khách yêu cầu tầng cao');
    await clickAndRender(fixture.nativeElement.querySelector('[data-action="apply-room-assignment"]'));

    expect(updateRoomAssignment).toHaveBeenCalledWith(
      88,
      { roomIds: [12, 13], reason: 'Đổi phòng do khách yêu cầu tầng cao' },
      expect.any(String),
    );
    expect(fixture.nativeElement.textContent).toContain('Đã gán lại phòng');
  });

  it('refreshes after a conflict and requires the operator to reconfirm', async () => {
    const assignedContext: AvailableRoomContext = {
      ...context,
      assignedRooms: [
        { ...context.candidates[0], status: 'RESERVED' },
        { ...context.candidates[1], status: 'RESERVED' },
      ],
      assignedRoomIds: [11, 12],
      candidates: [context.candidates[2]],
    };
    getAvailableRoomContext.mockReturnValue(of(assignedContext));
    updateRoomAssignment.mockReturnValue(throwError(() => ({
      status: 409,
      error: { code: 'CONCURRENT_MODIFICATION', message: 'Inventory changed' },
    })));
    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));
    const cards = fixture.nativeElement.querySelectorAll('.room-card') as NodeListOf<HTMLElement>;
    await clickAndRender(cards[0]);
    await clickAndRender(cards[2]);
    await enterReason('Đổi phòng do khách yêu cầu tầng cao');

    await clickAndRender(fixture.nativeElement.querySelector('[data-action="apply-room-assignment"]'));

    expect(fixture.componentInstance.mutationError).toContain('Kho phòng vừa thay đổi');
    expect(fixture.componentInstance.selectionChanged).toBe(false);
    expect(getAvailableRoomContext).toHaveBeenCalledTimes(3);
  });

  it('requires reason and explicit confirmation before releasing rooms', async () => {
    getAvailableRoomContext.mockReturnValue(of({
      ...context,
      requiredQuantity: 1,
      assignedRooms: [{ ...context.candidates[0], status: 'RESERVED' }],
      assignedRoomIds: [11],
      candidates: [],
    }));
    await clickAndRender(fixture.nativeElement.querySelector('.refresh'));
    expect(fixture.nativeElement.querySelector('[data-action="begin-room-release"]')?.disabled).toBe(true);

    await enterReason('Giải phóng để xử lý bảo trì');
    await clickAndRender(fixture.nativeElement.querySelector('[data-action="begin-room-release"]'));
    expect(fixture.nativeElement.querySelector('[data-action="confirm-room-release"]')).toBeTruthy();
    await clickAndRender(fixture.nativeElement.querySelector('[data-action="confirm-room-release"]'));

    expect(releaseRoomAssignment).toHaveBeenCalledWith(88, 'Giải phóng để xử lý bảo trì', expect.any(String));
  });

  it('keeps view-only users from selecting or mutating rooms', async () => {
    fixture.componentRef.setInput('allowMutation', false);
    fixture.detectChanges();
    const firstCard = fixture.nativeElement.querySelector('.room-card') as HTMLButtonElement;
    expect(firstCard.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.assignment-command')).toBeNull();
    await clickAndRender(firstCard);
    expect(updateRoomAssignment).not.toHaveBeenCalled();
    expect(releaseRoomAssignment).not.toHaveBeenCalled();
  });
});
