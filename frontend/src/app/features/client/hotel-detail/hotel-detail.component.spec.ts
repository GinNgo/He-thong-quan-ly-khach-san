import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Params, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { ClientApiService } from '../../../core/services/client-api.service';
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
