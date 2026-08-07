import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { ClientApiService } from '../../../core/services/client-api.service';
import { AuthService } from '../../../core/services/auth';
import { FavoriteService } from '../../../core/services/favorite.service';
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
        { provide: AuthService, useValue: { logout$: new Subject<void>(), isLoggedIn: () => false } },
        { provide: FavoriteService, useValue: { favorites: signal([]), ensureLoaded: () => of([]), isFavorite: () => false, add: vi.fn(), remove: vi.fn() } },
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

  it('renders canonical quote totals, member tier, and typed sponsored disclosure', async () => {
    const room = {
      id: 901,
      code: 'DELUXE',
      nameVi: 'Deluxe',
      nameEn: 'Deluxe',
      maxGuest: 2,
      maxGuests: 2,
      maxAdults: 2,
      maxChildren: 1,
      basePrice: 500000,
      descriptionVi: 'Deluxe',
      descriptionEn: 'Deluxe',
      availableRooms: 2,
    };
    const hotel = {
      id: 44,
      name: 'LuxeStay Riverside',
      addressLine: '1 River Road',
      starRating: 4,
      latitude: 10.7,
      longitude: 106.7,
      propertyType: 'HOTEL',
      sponsoredPlacement: {
        placementId: 77,
        placementKind: 'SPONSORED',
        disclosureVi: 'Được tài trợ',
        disclosureEn: 'Sponsored',
        endsAt: '2026-08-04T00:00:00Z',
      },
    };
    api.getHotelById.mockReturnValue(of(hotel));
    api.getRoomTypesByHotel.mockReturnValue(of([room]));
    params$.next(convertToParamMap({ id: '44' }));
    fixture.detectChanges();
    await fixture.whenStable();

    component.selectQuantity(room, 1);
    component.bookingQueryParams = {
      checkIn: '2026-08-10',
      checkOut: '2026-08-12',
      adultCount: 2,
      childCount: 0,
      roomCount: 1,
    };
    component.selectedQuote = {
      quoteId: 'quote-44',
      expiresAt: '2026-08-10T12:15:00Z',
      propertyId: 44,
      roomTypeId: 901,
      nightlyPrice: 500000,
      numberOfNights: 2,
      roomQuantity: 1,
      baseSubtotal: 1000000,
      taxAmount: 120000,
      feeAmount: 15000,
      taxesAndFees: 135000,
      appliedPromotions: [{
        campaignId: 71,
        code: 'MEMBER10',
        applicationType: 'AUTOMATIC',
        nameVi: 'Giá thành viên',
        nameEn: 'Member price',
        discountAmount: 100000,
      }],
      memberBenefit: {
        eligible: true,
        tierCode: 'GOLD',
        tierNameVi: 'Vàng',
        tierNameEn: 'Gold',
      },
      totalDiscount: 100000,
      finalTotal: 1035000,
      currency: 'VND',
    };
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-sponsored="true"]')).toBeTruthy();
    const summary = fixture.nativeElement.querySelector('.booking-summary') as HTMLElement;
    expect(summary.textContent).toContain('1.000.000');
    expect(summary.querySelector('.promotion-proof')?.textContent).toContain('Vàng');
    expect(summary.querySelector('.summary-total strong')?.textContent).toContain('1.035.000');
  });
});
