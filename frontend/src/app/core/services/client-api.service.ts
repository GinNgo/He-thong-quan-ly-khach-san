import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { PaymentLifecycleSummary, RefundSummary } from './reservation.service';

export interface Hotel {
  id: number;
  name: string;
  addressLine: string;
  mainImage?: string;
  mainImageUrl?: string;
  starRating: number;
  latitude: number;
  longitude: number;
  distanceKm?: number;
  distanceText?: string;
  startingPrice?: number;
  approvalStatus?: string;
  city?: string;
  country?: string;
  description?: string;
  slug?: string;
  thumbnailUrl?: string;
  galleryUrls?: string[];
  imageCount?: number;
  imageAltText?: string;
  propertyType?: string;
  provinceName?: string;
  wardName?: string;
  reviewScore?: number;
  reviewCount?: number;
  availableRoomCount?: number;
  amenities?: string[];
  sponsoredPlacement?: PublicPlacementDisclosure;
  lowestRoomType?: { id: number; name: string; maxGuests: number };
  pricing?: {
    nightlyPrice: number;
    discountedNightlyPrice?: number;
    discountedPrice: number;
    numberOfNights: number;
    roomQuantity?: number;
    subtotal?: number;
    taxAmount: number;
    feeAmount: number;
    totalAmount: number;
    currency: string;
  };
  quote?: PromotionQuote;
  property?: {
    id: number;
    name: string;
    address?: string;
    phone?: string;
    email?: string;
    contactName?: string;
  };
}

export interface PublicPlacementDisclosure {
  placementId: number;
  placementKind: 'SPONSORED';
  disclosureVi: string;
  disclosureEn: string;
  endsAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface RoomType {
  id: number;
  hotelId?: number;
  code: string;
  nameVi: string;
  nameEn: string;
  maxGuest: number;
  maxAdults?: number;
  maxChildren?: number;
  maxGuests?: number;
  bedType?: string;
  bedCount?: number;
  basePrice: number;
  descriptionVi: string;
  descriptionEn: string;
  availableRooms?: number;
  nights?: number;
  totalPrice?: number;
  quote?: PromotionQuote;
  imageUrls?: string[];
}

export interface PromotionQuoteRequest {
  propertyId: number;
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  quantity: number;
  adultCount: number;
  childCount: number;
  couponCode?: string;
}

export interface PromotionQuote {
  quoteId: string;
  expiresAt: string;
  propertyId: number;
  roomTypeId: number;
  nightlyPrice: number;
  numberOfNights: number;
  roomQuantity: number;
  baseSubtotal: number;
  taxAmount: number;
  feeAmount: number;
  taxesAndFees: number;
  appliedPromotions: Array<{
    campaignId: number;
    code: string;
    applicationType: 'AUTOMATIC' | 'COUPON';
    nameVi: string;
    nameEn?: string | null;
    discountAmount: number;
  }>;
  memberBenefit: {
    eligible: boolean;
    tierCode?: string | null;
    tierNameVi?: string | null;
    tierNameEn?: string | null;
    explanation?: string | null;
  };
  totalDiscount: number;
  finalTotal: number;
  currency: 'VND';
}

export interface PublicPromotion {
  id: number;
  code: string;
  propertyId?: number | null;
  nameVi: string;
  nameEn?: string | null;
  applicationType: 'AUTOMATIC' | 'COUPON';
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  maxDiscount?: number | null;
  endsAt: string;
  memberOnly: boolean;
  requiredTierCodes: string[];
}

export interface ReservationRequest {
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
  firstName: string;
  lastName: string;
  phone: string;
  paymentMethod: string;
  cancellationReasonCode?: string;
  cancellationReason?: string;
  cancelledAt?: string;
  quantity?: number;
  adults?: number;
  children?: number;
  specialRequests?: string;
  couponCode?: string;
}

export interface ReservationSummary {
  id: number;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
  quantity?: number;
  adults?: number;
  children?: number;
  totalAmount: number;
  status: string;
  paymentMethod: string;
  payment?: PaymentLifecycleSummary;
  refunds?: RefundSummary[];
  quote?: PromotionQuote;
  property?: {
    id: number;
    name: string;
    address?: string;
    phone?: string;
    email?: string;
    contactName?: string;
  };
  details?: Array<{
    id: number;
    roomId: number;
    roomNumber: string;
    priceAtBooking: number;
  }>;
}

export interface LocationSuggestion {
  type: 'PROVINCE' | 'WARD' | 'PROPERTY' | 'LANDMARK';
  id: number;
  parentId?: number;
  name: string;
  displayName: string;
  secondaryText?: string;
  address?: string;
  provinceId?: number;
  provinceName?: string;
  wardId?: number;
  wardName?: string;
  propertyCount?: number;
  slug?: string;
  propertyType?: string;
  thumbnailUrl?: string;
  imageUrl?: string;
  reviewScore?: number;
  distanceKm?: number;
  latitude?: number;
  longitude?: number;
  defaultRadiusKm?: number;
  category?: string;
  descriptionVi?: string;
  descriptionEn?: string;
}

export interface SearchSuggestionGroups {
  provinces: LocationSuggestion[];
  wards: LocationSuggestion[];
  properties: LocationSuggestion[];
  landmarks: LocationSuggestion[];
}

export type HomeRecommendationReason =
  | 'SEARCH_CONTEXT'
  | 'POPULAR_DESTINATION'
  | 'TOP_RATED';

export interface HomeRecommendationDestination {
  readonly id: number;
  readonly name: string;
  readonly displayName: string;
  readonly propertyCount: number;
  readonly selectedByDefault: boolean;
}

export interface HomeRecommendationPricing {
  readonly nightlyPrice: number;
  readonly finalNightlyPrice?: number | null;
  readonly totalDiscount?: number | null;
  readonly currency: 'VND';
}

export interface HomeRecommendationItem {
  readonly propertyId: number;
  readonly name: string;
  readonly propertyType: string;
  readonly provinceId: number;
  readonly provinceName: string;
  readonly wardName?: string | null;
  readonly imageUrl?: string | null;
  readonly imageAlt?: string | null;
  readonly starRating?: number | null;
  readonly reviewScore?: number | null;
  readonly reviewCount?: number | null;
  readonly availableRoomCount?: number | null;
  readonly pricing?: HomeRecommendationPricing | null;
  readonly quote?: PromotionQuote | null;
  readonly recommendationReason: HomeRecommendationReason;
  readonly sponsored: false;
}

export interface HomeRecommendationResponse {
  readonly destination: HomeRecommendationDestination;
  readonly items: readonly HomeRecommendationItem[];
  readonly totalAvailable: number;
}

export interface HomeRecommendationQuery {
  readonly provinceId: number;
  readonly checkInDate?: string;
  readonly checkOutDate?: string;
  readonly stayType?: 'OVERNIGHT' | 'DAY_USE';
  readonly adultCount?: number;
  readonly childCount?: number;
  readonly roomCount?: number;
  readonly limit?: number;
  readonly locale?: 'vi' | 'en';
}

export interface HomeSpotlightTarget {
  readonly type: 'PROPERTY' | 'SEARCH_COLLECTION';
  readonly propertyId?: number | null;
  readonly route: string;
  readonly query?: Readonly<Record<string, string>>;
}

export interface HomeSpotlight {
  readonly id: number;
  readonly kind: 'EDITORIAL' | 'SPONSORED';
  readonly title: string;
  readonly description?: string | null;
  readonly imageUrl: string;
  readonly imageAlt: string;
  readonly disclosure: string;
  readonly target: HomeSpotlightTarget;
  readonly startsAt: string;
  readonly endsAt: string;
}

export interface UserContext {
  id: number;
  username: string;
  email: string;
  emailVerified?: boolean;
  emailVerifiedAt?: string;
  pendingEmail?: string;
  fullName?: string;
  phone?: string;
  avatarUrl?: string;
  status?: string;
  points?: number;
  roles: Array<string | { id?: number; code: string; name?: string }>;
  plan?: string;
  subscriptionStatus?: string;
  assignedProperties?: Array<{ id: number; name: string }>;
  partnerRegistrationStatus?: 'NONE' | 'PENDING' | 'APPROVED';
  unreadMessageCount?: number;
  pendingBookingCount?: number;
}

@Injectable({
  providedIn: 'root',
})
export class ClientApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
  private readonly popularDestinationsCache = new Map<number, Observable<LocationSuggestion[]>>();
  private hotelApiUrl = `${environment.apiUrl}/v1/hotels`;

  searchHotels(paramsObj: any): Observable<PagedResponse<Hotel>> {
    let params = new HttpParams();
    Object.keys(paramsObj).forEach((key) => {
      if (paramsObj[key] !== null && paramsObj[key] !== undefined) {
        params = params.set(key, String(paramsObj[key]));
      }
    });

    return this.http.get<PagedResponse<Hotel>>(`${environment.apiUrl}/public/properties/search`, {
      params,
    });
  }

  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.hotelApiUrl}/public/${id}`);
  }

  getAccessibleHotels(): Observable<Hotel[]> {
    return this.http.get<Hotel[]>(`${this.hotelApiUrl}/accessible`);
  }

  getProvinces(): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/public/locations/provinces`);
  }

  getPopularProvinces(size: number = 6): Observable<LocationSuggestion[]> {
    const params = new HttpParams().set('size', size.toString());
    return this.http.get<LocationSuggestion[]>(
      `${environment.apiUrl}/public/locations/provinces/popular`,
      { params },
    );
  }

  getAvailableRooms(
    hotelId: number,
    checkIn: string,
    checkOut: string,
    guests: number,
  ): Observable<any[]> {
    let params = new HttpParams()
      .set('checkIn', checkIn)
      .set('checkOut', checkOut)
      .set('guests', guests.toString());

    return this.http.get<any[]>(`${this.apiUrl}/hotels/${hotelId}/available-rooms`, { params });
  }

  submitPropertyClaim(propertyId: number, data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/properties/${propertyId}/claim`, data);
  }

  getRoomTypesByHotel(
    hotelId: number,
    checkIn?: string,
    checkOut?: string,
    guests?: number,
  ): Observable<RoomType[]> {
    let params = new HttpParams();
    if (checkIn) params = params.set('checkIn', checkIn);
    if (checkOut) params = params.set('checkOut', checkOut);
    if (guests) params = params.set('guests', guests);

    return this.http.get<RoomType[]>(`${this.apiUrl}/room-types/public/hotel/${hotelId}`, {
      params,
    });
  }

  getPromotionQuote(request: PromotionQuoteRequest): Observable<PromotionQuote> {
    return this.http.post<PromotionQuote>(`${this.apiUrl}/public/quotes`, request);
  }

  getPublicPromotions(limit = 6): Observable<PublicPromotion[]> {
    const params = new HttpParams().set('limit', String(Math.min(Math.max(limit, 1), 12)));
    return this.http.get<PublicPromotion[]>(`${this.apiUrl}/public/promotions`, { params });
  }

  getMyMembership(): Observable<PromotionQuote['memberBenefit']> {
    return this.http.get<PromotionQuote['memberBenefit']>(`${this.apiUrl}/public/promotions/membership`);
  }

  bookRoom(reservation: ReservationRequest, idempotencyKey?: string): Observable<any> {
    const options = idempotencyKey
      ? { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) }
      : {};
    return this.http.post(`${this.apiUrl}/reservations/book`, reservation, options);
  }

  getMyBookings(): Observable<ReservationSummary[]> {
    return this.http.get<ReservationSummary[]>(`${this.apiUrl}/reservations/my-bookings`);
  }

  getProfile(): Observable<UserContext> {
    return this.http.get<UserContext>(`${this.apiUrl}/users/me`);
  }

  searchLocations(keyword: string, size: number = 20): Observable<LocationSuggestion[]> {
    let params = new HttpParams().set('keyword', keyword).set('size', size.toString());
    return this.http.get<LocationSuggestion[]>(`${environment.apiUrl}/public/locations/search`, {
      params,
    });
  }

  searchAutocomplete(keyword: string): Observable<LocationSuggestion[]> {
    return this.searchLocations(keyword, 15);
  }

  getSearchSuggestions(
    keyword: string,
    limit: number = 10,
    latitude?: number,
    longitude?: number,
    provinceId?: number,
  ): Observable<SearchSuggestionGroups> {
    let params = new HttpParams().set('keyword', keyword).set('limit', limit.toString());
    if (latitude !== undefined) params = params.set('latitude', latitude.toString());
    if (longitude !== undefined) params = params.set('longitude', longitude.toString());
    if (provinceId !== undefined) params = params.set('provinceId', provinceId.toString());
    return this.http.get<SearchSuggestionGroups>(
      `${environment.apiUrl}/public/search/suggestions`,
      { params },
    );
  }

  getPopularDestinations(limit: number = 8): Observable<LocationSuggestion[]> {
    const safeLimit = Math.min(Math.max(limit, 1), 12);
    const cached = this.popularDestinationsCache.get(safeLimit);
    if (cached) return cached;

    const params = new HttpParams().set('limit', safeLimit.toString());
    const request = this.http
      .get<LocationSuggestion[]>(`${environment.apiUrl}/public/popular-destinations`, { params })
      .pipe(
        catchError((error) => {
          this.popularDestinationsCache.delete(safeLimit);
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    this.popularDestinationsCache.set(safeLimit, request);
    return request;
  }

  getHomeRecommendationDestinations(
    preferredProvinceId?: number,
    limit: number = 5,
    locale: 'vi' | 'en' = 'vi',
  ): Observable<HomeRecommendationDestination[]> {
    let params = new HttpParams()
      .set('limit', Math.min(Math.max(limit, 1), 8).toString())
      .set('locale', locale);
    if (preferredProvinceId !== undefined) {
      params = params.set('preferredProvinceId', preferredProvinceId.toString());
    }
    return this.http.get<HomeRecommendationDestination[]>(
      `${environment.apiUrl}/public/home/recommendation-destinations`,
      { params },
    ).pipe(
      // Older deployed APIs do not expose the recommendation endpoint yet.
      // Popular destinations preserve a useful home experience until that API is available.
      catchError(() => this.getPopularDestinations(limit).pipe(
        map(destinations => destinations.map((destination, index) => ({
          id: destination.provinceId ?? destination.id,
          name: destination.name,
          displayName: destination.displayName,
          propertyCount: destination.propertyCount ?? 0,
          selectedByDefault: index === 0,
        }))),
      )),
    );
  }

  getHomeRecommendations(query: HomeRecommendationQuery): Observable<HomeRecommendationResponse> {
    let params = new HttpParams()
      .set('provinceId', query.provinceId.toString())
      .set('limit', Math.min(Math.max(query.limit ?? 8, 1), 12).toString())
      .set('locale', query.locale ?? 'vi');
    if (query.checkInDate) params = params.set('checkInDate', query.checkInDate);
    if (query.checkOutDate) params = params.set('checkOutDate', query.checkOutDate);
    if (query.stayType) params = params.set('stayType', query.stayType);
    if (query.adultCount !== undefined) params = params.set('adultCount', query.adultCount.toString());
    if (query.childCount !== undefined) params = params.set('childCount', query.childCount.toString());
    if (query.roomCount !== undefined) params = params.set('roomCount', query.roomCount.toString());
    return this.http.get<HomeRecommendationResponse>(
      `${environment.apiUrl}/public/home/recommendations`,
      { params },
    ).pipe(
      catchError(() => this.searchHotels({
        provinceId: query.provinceId,
        pageNumber: 1,
        pageSize: Math.min(Math.max(query.limit ?? 8, 1), 12),
      }).pipe(
        map(page => ({
          destination: {
            id: query.provinceId,
            name: '',
            displayName: '',
            propertyCount: page.content?.length ?? 0,
            selectedByDefault: true,
          },
          items: (page.content ?? []).map(property => ({
            propertyId: property.id,
            name: property.name,
            propertyType: property.propertyType ?? 'HOTEL',
            provinceId: query.provinceId,
            provinceName: property.provinceName ?? '',
            wardName: property.wardName,
            imageUrl: property.mainImageUrl ?? property.mainImage,
            imageAlt: property.imageAltText,
            starRating: property.starRating,
            reviewScore: property.reviewScore,
            reviewCount: property.reviewCount,
            availableRoomCount: property.availableRoomCount,
            pricing: property.startingPrice === undefined ? null : {
              nightlyPrice: property.startingPrice,
              currency: 'VND' as const,
            },
            recommendationReason: 'POPULAR_DESTINATION' as const,
            sponsored: false as const,
          })),
          totalAvailable: page.totalElements ?? page.content?.length ?? 0,
        })),
      )),
    );
  }

  getHomeSpotlights(limit: number = 6, locale: 'vi' | 'en' = 'vi'): Observable<HomeSpotlight[]> {
    const params = new HttpParams()
      .set('limit', Math.min(Math.max(limit, 1), 10).toString())
      .set('locale', locale);
    return this.http.get<HomeSpotlight[]>(
      `${environment.apiUrl}/public/home/spotlights`,
      { params },
    ).pipe(catchError(() => of(this.localHomeSpotlights(locale).slice(0, Math.min(Math.max(limit, 1), 10)))));
  }

  /** Keeps the editorial rail useful while older API images are being upgraded. */
  private localHomeSpotlights(locale: 'vi' | 'en'): HomeSpotlight[] {
    const english = locale === 'en';
    const items: Array<[string, string, string, string]> = english
      ? [
        ['Discover Hanoi heritage stays', 'A curated selection for your next city break.', 'Hanoi', '01'],
        ['Da Nang by the coast', 'Wake up close to beaches, food and local life.', 'Da Nang', '04'],
        ['A slower Phu Quoc escape', 'Find a quiet island stay for your next reset.', 'Phu Quoc', '06'],
        ['Weekend in Ho Chi Minh City', 'Hand-picked stays in the city that never sleeps.', 'Ho Chi Minh City', '02'],
      ]
      : [
        ['Khám phá nơi ở giữa lòng Hà Nội', 'Gợi ý lưu trú chọn lọc cho chuyến đi sắp tới.', 'Hà Nội', '01'],
        ['Đà Nẵng bên bờ biển', 'Tận hưởng biển xanh, ẩm thực và nhịp sống địa phương.', 'Đà Nẵng', '04'],
        ['Một Phú Quốc thật chậm', 'Tìm nơi nghỉ yên bình cho kỳ nghỉ tiếp theo.', 'Phú Quốc', '06'],
        ['Cuối tuần ở Thành phố Hồ Chí Minh', 'Những nơi ở nổi bật giữa thành phố không ngủ.', 'Thành phố Hồ Chí Minh', '02'],
      ];
    return items.map(([title, description, location, image], index) => ({
      id: 9000 + index,
      kind: 'EDITORIAL' as const,
      title,
      description,
      imageUrl: `assets/destinations/destination-${image}.webp`,
      imageAlt: title,
      disclosure: english ? 'LuxeStay editorial' : 'LuxeStay tuyển chọn',
      target: { type: 'SEARCH_COLLECTION' as const, route: '/search', query: { displayLocation: location } },
      startsAt: '2020-01-01T00:00:00Z',
      endsAt: '2035-12-31T23:59:59Z',
    }));
  }
}
