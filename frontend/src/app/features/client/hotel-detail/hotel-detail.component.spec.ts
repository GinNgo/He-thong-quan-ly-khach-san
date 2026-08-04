import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Params, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';
import { HotelDetailComponent } from './hotel-detail.component';

describe('HotelDetailComponent', () => {
  let fixture: ComponentFixture<HotelDetailComponent>;
  let component: HotelDetailComponent;
  let params$: Subject<ParamMap>;
  let queryParams$: BehaviorSubject<Params>;
  let api: { getHotelById: ReturnType<typeof vi.fn>; getRoomTypesByHotel: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    params$ = new Subject<ParamMap>();
    queryParams$ = new BehaviorSubject<Params>({});
    api = {
      getHotelById: vi.fn(() => throwError(() => ({ status: 404 }))),
      getRoomTypesByHotel: vi.fn(() => of([]))
    };

    await TestBed.configureTestingModule({
      imports: [HotelDetailComponent],
      providers: [
        provideRouter([]),
        { provide: ClientApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { queryParams: queryParams$, paramMap: params$, snapshot: { fragment: null } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('exposes a distinct availability loading state before the route resolves', () => {
    expect(fixture.nativeElement.querySelector('[data-availability-loading]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-availability-empty]')).toBeNull();
  });

  it('renders a recoverable state for an invalid route parameter', () => {
    params$.next(convertToParamMap({ id: 'not-a-number' }));
    fixture.detectChanges();

    expect(component.pageError).toContain('không hợp lệ');
    expect(api.getHotelById).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Tìm chỗ nghỉ khác');
  });

  it('renders a not-found recovery state when the API returns 404', () => {
    params$.next(convertToParamMap({ id: '999999' }));
    fixture.detectChanges();

    expect(api.getHotelById).toHaveBeenCalledWith(999999);
    expect(component.pageError).toContain('Không tìm thấy chỗ nghỉ này');
    expect(fixture.nativeElement.textContent).toContain('Chuyến đi vẫn có thể tiếp tục');
  });

  it('hides stale property details when the public room catalog becomes unavailable', () => {
    api.getHotelById.mockReturnValue(of({ id: 44, name: 'Stale property' }));
    api.getRoomTypesByHotel.mockReturnValue(throwError(() => ({ status: 404 })));

    params$.next(convertToParamMap({ id: '44' }));
    fixture.detectChanges();

    expect(api.getRoomTypesByHotel).toHaveBeenCalledWith(44, undefined, undefined, 2);
    expect(component.hotel).toBeNull();
    expect(component.roomTypes).toEqual([]);
    expect(component.pageError).toContain('Không tìm thấy chỗ nghỉ này');
  });

  it('preserves canonical stay dates in the detail availability request and renders authoritative room states', () => {
    api.getHotelById.mockReturnValue(of({
      id: 44,
      name: 'Availability Hotel',
      addressLine: '44 Test Street',
      starRating: 4,
      latitude: 10,
      longitude: 106,
    }));
    api.getRoomTypesByHotel.mockReturnValue(of([
      roomType(1, 3),
      roomType(2, 0),
      roomType(3, undefined),
    ]));
    queryParams$.next({
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      adultCount: '2',
      childCount: '1',
      roomCount: '2',
    });

    params$.next(convertToParamMap({ id: '44' }));
    fixture.detectChanges();

    expect(api.getRoomTypesByHotel).toHaveBeenCalledWith(44, '2026-08-10', '2026-08-12', 3);
    expect(fixture.nativeElement.querySelector('[data-room-availability-count][data-room-id="1"]')
      ?.getAttribute('data-availability-value')).toBe('3');
    expect(fixture.nativeElement.querySelector('[data-room-availability-unavailable][data-room-id="2"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-room-availability-missing][data-room-id="3"]')).not.toBeNull();
    expect((fixture.nativeElement.querySelector('[data-room-quantity="1"]') as HTMLSelectElement).disabled).toBe(false);
    expect((fixture.nativeElement.querySelector('[data-room-quantity="2"]') as HTMLSelectElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('[data-room-quantity="3"]') as HTMLSelectElement).disabled).toBe(true);
  });

  it('cancels the previous property request so the latest route wins', () => {
    const firstHotel$ = new Subject<Hotel>();
    const latestHotel$ = new Subject<Hotel>();
    api.getHotelById.mockImplementation((id: number) => id === 44 ? firstHotel$ : latestHotel$);

    params$.next(convertToParamMap({ id: '44' }));
    expect(firstHotel$.observed).toBe(true);
    params$.next(convertToParamMap({ id: '45' }));

    expect(firstHotel$.observed).toBe(false);
    firstHotel$.next({ id: 44, name: 'Stale property' } as Hotel);
    latestHotel$.next({ id: 45, name: 'Latest property' } as Hotel);
    fixture.detectChanges();

    expect(component.hotel?.id).toBe(45);
    expect(api.getRoomTypesByHotel).toHaveBeenCalledWith(45, undefined, undefined, 2);
  });

  it('clears stale rooms and only renders the latest query response', () => {
    const firstRooms$ = new Subject<RoomType[]>();
    const latestRooms$ = new Subject<RoomType[]>();
    api.getHotelById.mockReturnValue(of({ id: 44, name: 'Query hotel' } as Hotel));
    api.getRoomTypesByHotel.mockReturnValueOnce(firstRooms$).mockReturnValueOnce(latestRooms$);

    params$.next(convertToParamMap({ id: '44' }));
    firstRooms$.next([roomType(1, 2)]);
    firstRooms$.complete();
    fixture.detectChanges();
    expect(component.roomTypes[0]?.id).toBe(1);

    queryParams$.next({ checkIn: '2026-08-20', checkOut: '2026-08-22', adultCount: '3', childCount: '0', roomCount: '1' });
    fixture.detectChanges();

    expect(component.roomTypes).toEqual([]);
    expect(component.isRoomLoading).toBe(true);
    latestRooms$.next([roomType(2, 1)]);
    latestRooms$.complete();
    fixture.detectChanges();

    expect(component.roomTypes.map(room => room.id)).toEqual([2]);
    expect(component.isRoomLoading).toBe(false);
  });

  it('retries a transient room error using the latest route and query', () => {
    api.getHotelById.mockReturnValue(of({ id: 44, name: 'Retry hotel' } as Hotel));
    api.getRoomTypesByHotel
      .mockReturnValueOnce(throwError(() => ({ status: 503 })))
      .mockReturnValueOnce(of([roomType(3, 1)]));
    queryParams$.next({ checkIn: '2026-08-20', checkOut: '2026-08-22', adultCount: '2', childCount: '1', roomCount: '1' });

    params$.next(convertToParamMap({ id: '44' }));
    fixture.detectChanges();
    const retry = fixture.nativeElement.querySelector('[data-room-retry]') as HTMLButtonElement;
    expect(retry).not.toBeNull();

    retry.click();
    fixture.detectChanges();

    expect(api.getRoomTypesByHotel).toHaveBeenLastCalledWith(44, '2026-08-20', '2026-08-22', 3);
    expect(component.roomError).toBe('');
    expect(component.roomTypes[0]?.id).toBe(3);
  });

  it('cancels in-flight requests when the component is destroyed', () => {
    const hotel$ = new Subject<Hotel>();
    api.getHotelById.mockReturnValue(hotel$);

    params$.next(convertToParamMap({ id: '44' }));
    expect(hotel$.observed).toBe(true);
    fixture.destroy();

    expect(hotel$.observed).toBe(false);
  });
});

function roomType(id: number, availableRooms: number | undefined) {
  return {
    id,
    code: `ROOM-${id}`,
    nameVi: `Phòng ${id}`,
    nameEn: `Room ${id}`,
    maxGuest: 3,
    basePrice: 500000,
    descriptionVi: '',
    descriptionEn: '',
    availableRooms,
  };
}
