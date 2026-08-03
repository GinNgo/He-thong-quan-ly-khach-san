import { Router } from '@angular/router';
import { HomeSearchStateService } from './home-search-state.service';

describe('HomeSearchStateService', () => {
  let service: HomeSearchStateService;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    if (typeof localStorage !== 'undefined') localStorage.clear();
    navigate = vi.fn();
    service = new HomeSearchStateService({ navigate } as unknown as Router);
  });

  it('blocks a search without check-in and exposes an actionable validation error', () => {
    service.state.update(state => ({ ...state, checkInDate: null, checkOutDate: null }));

    expect(service.submitSearch()).toBe(false);
    expect(service.validationError()).toEqual({
      code: 'CHECK_IN_REQUIRED',
      message: 'Vui lòng chọn ngày nhận phòng.'
    });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('blocks an overnight range whose checkout is not after check-in', () => {
    const checkIn = futureDate(4);
    service.state.update(state => ({ ...state, checkInDate: checkIn, checkOutDate: checkIn }));

    expect(service.submitSearch()).toBe(false);
    expect(service.validationError()?.code).toBe('INVALID_DATE_RANGE');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('serializes location, dates, guests and property types into the search URL', () => {
    const checkIn = futureDate(3);
    const checkOut = futureDate(5);
    service.state.update(state => ({
      ...state,
      keyword: '',
      locationDisplayName: 'Mỹ Tho, Đồng Tháp',
      selectedSuggestionType: 'WARD',
      provinceId: 82,
      wardId: 8201,
      propertyTypes: ['HOTEL', 'HOMESTAY'],
      checkInDate: checkIn,
      checkOutDate: checkOut,
      adultCount: 3,
      childCount: 1,
      roomCount: 2
    }));

    expect(service.submitSearch()).toBe(true);
    expect(service.validationError()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: expect.objectContaining({
        displayLocation: 'Mỹ Tho, Đồng Tháp',
        provinceId: 82,
        wardId: 8201,
        propertyTypes: 'HOTEL,HOMESTAY',
        adultCount: 3,
        childCount: 1,
        roomCount: 2
      })
    });
  });

  it('clears stale validation as soon as the date selection changes', () => {
    service.state.update(state => ({ ...state, checkInDate: null, checkOutDate: null }));
    service.submitSearch();

    service.updateDates(futureDate(2), futureDate(3));

    expect(service.validationError()).toBeNull();
  });

  function futureDate(days: number): Date {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() + days);
    return date;
  }
});
