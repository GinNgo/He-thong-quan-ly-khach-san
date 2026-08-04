import { HttpErrorResponse } from '@angular/common/http';

import {
  propertySearchErrorState,
  propertySearchParamsFromRoute,
  validSearchStayDates,
} from './property-search-query';

describe('property search query contract', () => {
  it('normalizes every recognized API parameter and drops UI-only or unknown route state', () => {
    expect(propertySearchParamsFromRoute({
      keyword: '  river  ',
      provinceId: '82',
      wardId: '8201',
      landmarkId: '44',
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      adultCount: '2',
      childCount: '1',
      roomCount: '1',
      latitude: '10.36',
      longitude: '106.36',
      radiusKm: '8',
      sortBy: 'NEAREST',
      pageNumber: '1',
      pageSize: '20',
      propertyTypes: 'HOTEL,HOMESTAY',
      stayType: 'OVERNIGHT',
      minPrice: '300000',
      maxPrice: '1500000',
      starRatings: ['4', '5'],
      minReviewScore: '8',
      amenityIds: '3,7',
      freeCancellation: 'false',
      payAtProperty: false,
      breakfastIncluded: 'false',
      displayLocation: 'Dong Thap',
      propertyId: '91',
      _retry: '123',
      arbitrary: 'must-not-leak',
    })).toEqual({
      keyword: 'river',
      provinceId: 82,
      wardId: 8201,
      landmarkId: 44,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      adultCount: 2,
      childCount: 1,
      roomCount: 1,
      latitude: 10.36,
      longitude: 106.36,
      radiusKm: 8,
      sortBy: 'NEAREST',
      pageNumber: 1,
      pageSize: 20,
      propertyTypes: ['HOTEL', 'HOMESTAY'],
      stayType: 'OVERNIGHT',
      minPrice: 300000,
      maxPrice: 1500000,
      starRatings: [4, 5],
      minReviewScore: 8,
      amenityIds: [3, 7],
      freeCancellation: false,
      payAtProperty: false,
      breakfastIncluded: false,
    });
  });

  it('keeps the normal home-search contract while excluding display state', () => {
    expect(propertySearchParamsFromRoute({
      displayLocation: 'Tỉnh Đồng Tháp',
      provinceId: '82',
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-11',
      adultCount: '3',
      childCount: '0',
      roomCount: '2',
      propertyTypes: 'HOTEL',
    })).toEqual(expect.objectContaining({
      provinceId: 82,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-11',
      adultCount: 3,
      childCount: 0,
      roomCount: 2,
      propertyTypes: ['HOTEL'],
    }));
    expect(propertySearchParamsFromRoute({ displayLocation: 'Tỉnh Đồng Tháp' }))
      .not.toHaveProperty('displayLocation');
  });

  it('hydrates only a strict, increasing ISO stay range', () => {
    expect(validSearchStayDates({ checkInDate: '2026-08-10', checkOutDate: '2026-08-12' }))
      .toEqual({ checkIn: new Date(2026, 7, 10), checkOut: new Date(2026, 7, 12) });
    expect(validSearchStayDates({ checkInDate: '2026-02-30', checkOutDate: '2026-03-01' })).toBeNull();
    expect(validSearchStayDates({ checkInDate: '2026-08-12', checkOutDate: '2026-08-10' })).toBeNull();
  });

  it('presents backend invalid-request details without offering a blind retry', () => {
    const state = propertySearchErrorState(new HttpErrorResponse({
      status: 400,
      error: {
        code: 'INVALID_REQUEST',
        message: 'Ngày trả phòng phải sau ngày nhận phòng.',
      },
    }));

    expect(state).toEqual({
      title: 'Yêu cầu tìm kiếm không hợp lệ',
      message: 'Ngày trả phòng phải sau ngày nhận phòng.',
      code: 'INVALID_REQUEST',
      retryable: false,
    });
  });
});
