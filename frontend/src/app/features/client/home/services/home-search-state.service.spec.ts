<<<<<<< HEAD
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
    expect(navigate.mock.calls[0][1].queryParams).not.toHaveProperty('stayType');
  });

  it('keeps the public search contract overnight-only', () => {
    expect(service.state().stayType).toBe('OVERNIGHT');
    expect(service.state().checkOutDate?.getTime()).toBeGreaterThan(service.state().checkInDate?.getTime() ?? 0);
  });

  it('serializes a selected landmark with its authoritative radius and nearest sort', () => {
    service.selectSuggestion({
      type: 'LANDMARK',
      id: 42,
      name: 'Công viên Mỹ Tho',
      displayName: 'Công viên Mỹ Tho, Tỉnh Đồng Tháp',
      provinceId: 82,
      latitude: 10.3605,
      longitude: 106.3605,
      defaultRadiusKm: 8
    });

    expect(service.submitSearch()).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: expect.objectContaining({
        landmarkId: 42,
        provinceId: 82,
        radiusKm: 8,
        sortBy: 'NEAREST',
        latitude: 10.3605,
        longitude: 106.3605
      })
    });
  });

  it('hydrates a landmark from search URL state without degrading it to a province', () => {
    service.updateLocation('', 'Công viên Mỹ Tho, Tỉnh Đồng Tháp', 82, null,
      42, 10.3605, 106.3605, 8);

    expect(service.state()).toEqual(expect.objectContaining({
      selectedSuggestionType: 'LANDMARK', landmarkId: 42, provinceId: 82,
      latitude: 10.3605, longitude: 106.3605, radiusKm: 8
    }));
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
=======
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { HomeSearchStateService, RecentSearch } from './home-search-state.service';

describe('HomeSearchStateService', () => {
  const navigate = vi.fn();
  let service: HomeSearchStateService;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 6, 15, 12, 0, 0, 0));
    localStorage.clear();
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        HomeSearchStateService,
        { provide: Router, useValue: { navigate } },
      ],
    });
    service = TestBed.inject(HomeSearchStateService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps an invalid same-day overnight range visible and blocks submission', () => {
    const date = new Date(2026, 6, 29, 0, 0, 0, 0);

    service.updateDates(date, date);

    expect(service.state().checkOutDate?.getTime()).toBe(date.getTime());
    expect(service.dateValidationError()).toContain('sau ngày nhận phòng');
    expect(service.submitSearch()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('submits day-use with one local calendar date and no checkout parameter', () => {
    const date = new Date(2026, 11, 31, 0, 0, 0, 0);
    service.updateStayType('DAY_USE');
    service.updateDates(date, null);

    expect(service.submitSearch()).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: expect.objectContaining({
        stayType: 'DAY_USE',
        checkInDate: '2026-12-31',
      }),
    });
    const queryParams = navigate.mock.calls[0][1].queryParams;
    expect(queryParams.checkOutDate).toBeUndefined();
  });

  it('restores day-use recent searches without inventing a checkout date', () => {
    const recent: RecentSearch = {
      displayLocation: 'Đà Nẵng',
      keyword: '',
      provinceId: 48,
      wardId: null,
      propertyId: null,
      selectedSuggestionType: 'PROVINCE',
      stayType: 'DAY_USE',
      checkInDate: '2027-01-10',
      checkOutDate: null,
      adultCount: 2,
      childCount: 0,
      roomCount: 1,
      createdAt: new Date().toISOString(),
    };

    service.applyRecentSearch(recent);

    expect(service.state().stayType).toBe('DAY_USE');
    expect(service.state().checkOutDate).toBeNull();
  });

  it('serializes local calendar dates across a year boundary without a timezone shift', () => {
    const checkIn = new Date(2026, 11, 31, 23, 45);
    const checkOut = new Date(2027, 0, 1, 18, 30);

    service.updateDates(checkIn, checkOut);

    expect(service.state().checkInDate?.getHours()).toBe(0);
    expect(service.state().checkOutDate?.getHours()).toBe(0);
    expect(service.submitSearch()).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: expect.objectContaining({
        checkInDate: '2026-12-31',
        checkOutDate: '2027-01-01',
      }),
    });
  });

  it('repairs an invalid restored overnight range without changing its local check-in date', () => {
    const recent: RecentSearch = {
      displayLocation: 'Hà Nội',
      keyword: '',
      provinceId: 1,
      wardId: null,
      propertyId: null,
      selectedSuggestionType: 'PROVINCE',
      stayType: 'OVERNIGHT',
      checkInDate: '2027-03-15',
      checkOutDate: '2027-03-15',
      adultCount: 2,
      childCount: 1,
      roomCount: 1,
      createdAt: new Date().toISOString(),
    };

    service.applyRecentSearch(recent);

    expect(service.state().checkInDate).toEqual(new Date(2027, 2, 15));
    expect(service.state().checkOutDate).toEqual(new Date(2027, 2, 16));
    expect(service.dateValidationError()).toBe('');
  });

  it('persists the stay type and local dates in recent searches', () => {
    service.updateLocation('', 'Đà Nẵng', 48, null);
    service.updateDates(new Date(2027, 3, 30), new Date(2027, 4, 1));

    expect(service.submitSearch()).toBe(true);

    const stored = JSON.parse(localStorage.getItem('luxestay.recent-searches') || '[]');
    expect(stored[0]).toEqual(expect.objectContaining({
      displayLocation: 'Đà Nẵng',
      stayType: 'OVERNIGHT',
      checkInDate: '2027-04-30',
      checkOutDate: '2027-05-01',
    }));
  });

  it('persists landmark geography and defaults result sorting to nearest', () => {
    service.selectSuggestion({
      type: 'LANDMARK',
      id: 501,
      name: 'Cầu Rồng',
      displayName: 'Cầu Rồng, Đà Nẵng',
      provinceId: 48,
      latitude: 16.0611,
      longitude: 108.2277,
      defaultRadiusKm: 8,
    });

    expect(service.submitSearch()).toBe(true);
    expect(service.state()).toEqual(expect.objectContaining({
      selectedSuggestionType: 'LANDMARK',
      landmarkId: 501,
      provinceId: 48,
      latitude: 16.0611,
      longitude: 108.2277,
      radiusKm: 8,
    }));
    expect(navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: expect.objectContaining({
        landmarkId: 501,
        provinceId: 48,
        radiusKm: 8,
        sortBy: 'NEAREST',
      }),
    });
  });

  it('restores landmark context from a reloaded search URL', () => {
    service.restoreLocation({
      keyword: '',
      displayName: 'Hồ Hoàn Kiếm, Hà Nội',
      selectedSuggestionType: 'LANDMARK',
      provinceId: 1,
      wardId: null,
      landmarkId: 777,
      radiusKm: 10,
      latitude: 21.0287,
      longitude: 105.8521,
    });

    expect(service.state()).toEqual(expect.objectContaining({
      locationDisplayName: 'Hồ Hoàn Kiếm, Hà Nội',
      selectedSuggestionType: 'LANDMARK',
      landmarkId: 777,
      provinceId: 1,
      radiusKm: 10,
      latitude: 21.0287,
      longitude: 105.8521,
    }));
  });
>>>>>>> codex/ui-functional-audit-polish
});
