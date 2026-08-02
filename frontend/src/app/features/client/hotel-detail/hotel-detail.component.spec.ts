import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ClientApiService } from '../../../core/services/client-api.service';
import { HotelDetailComponent } from './hotel-detail.component';

describe('HotelDetailComponent', () => {
  let fixture: ComponentFixture<HotelDetailComponent>;
  let component: HotelDetailComponent;
  let params$: Subject<ParamMap>;
  let api: { getHotelById: ReturnType<typeof vi.fn>; getRoomTypesByHotel: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    params$ = new Subject<ParamMap>();
    api = {
      getHotelById: vi.fn(() => throwError(() => ({ status: 404 }))),
      getRoomTypesByHotel: vi.fn(() => of([]))
    };

    await TestBed.configureTestingModule({
      imports: [HotelDetailComponent],
      providers: [
        provideRouter([]),
        { provide: ClientApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { queryParams: of({}), paramMap: params$, snapshot: { fragment: null } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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
});
