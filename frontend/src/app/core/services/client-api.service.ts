import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, shareReplay, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

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
  imageProvenance?: string;
  propertyType?: string;
  provinceName?: string;
  wardName?: string;
  reviewScore?: number;
  reviewCount?: number;
  availableRoomCount?: number | null;
  amenities?: string[];
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
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PropertySearchParams {
  keyword?: string;
  provinceId?: number;
  wardId?: number;
  landmarkId?: number;
  checkInDate?: string;
  checkOutDate?: string;
  adultCount?: number;
  childCount?: number;
  roomCount?: number;
  latitude?: number;
  longitude?: number;
  radiusKm?: number;
  sortBy?: string;
  pageNumber?: number;
  pageSize?: number;
  propertyTypes?: string[];
  stayType?: string;
  minPrice?: number;
  maxPrice?: number;
  starRatings?: number[];
  minReviewScore?: number;
  amenityIds?: number[];
  freeCancellation?: boolean;
  payAtProperty?: boolean;
  breakfastIncluded?: boolean;
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
  availableRooms?: number | null;
  nights?: number;
  totalPrice?: number;
  imageUrls?: string[];
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
  quantity?: number;
  adults?: number;
  children?: number;
  specialRequests?: string;
  operationalPolicyVersion?: number;
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
  details?: Array<{
    id: number;
    roomId: number;
    roomNumber: string;
    priceAtBooking: number;
  }>;
}

interface ReservationSummaryPage {
  content: ReservationSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
  imageAltText?: string;
  imageProvenance?: string;
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
  providedIn: 'root'
})
export class ClientApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
  private readonly popularDestinationsTtlMs = 60_000;
  private readonly popularDestinationsCache = new Map<number, {
    expiresAt: number;
    response: Observable<LocationSuggestion[]>;
  }>();
  private hotelApiUrl = `${environment.apiUrl}/v1/hotels`;

  searchHotels(paramsObj: PropertySearchParams): Observable<PagedResponse<Hotel>> {
    let params = new HttpParams();
    for (const key of Object.keys(paramsObj) as Array<keyof PropertySearchParams>) {
      const value = paramsObj[key];
      if (value === null || value === undefined) continue;
      const serialized = Array.isArray(value)
        ? value.join(',')
        : typeof value === 'string' ? value.trim() : String(value);
      if (serialized) params = params.set(key, serialized);
    }

    return this.http.get<PagedResponse<Hotel>>(`${environment.apiUrl}/public/properties/search`, { params });
  }

  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.hotelApiUrl}/public/${id}`);
  }

  getProvinces(): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/public/locations/provinces`);
  }

  getPopularProvinces(size: number = 6): Observable<LocationSuggestion[]> {
    const params = new HttpParams().set('size', size.toString());
    return this.http.get<LocationSuggestion[]>(`${environment.apiUrl}/public/locations/provinces/popular`, { params });
  }

  getAvailableRooms(hotelId: number, checkIn: string, checkOut: string, guests: number): Observable<any[]> {
    let params = new HttpParams()
      .set('checkIn', checkIn)
      .set('checkOut', checkOut)
      .set('guests', guests.toString());

    return this.http.get<any[]>(`${this.apiUrl}/hotels/${hotelId}/available-rooms`, { params });
  }

  getRoomTypesByHotel(hotelId: number, checkIn?: string, checkOut?: string, guests?: number): Observable<RoomType[]> {
    let params = new HttpParams();
    if (checkIn) params = params.set('checkIn', checkIn);
    if (checkOut) params = params.set('checkOut', checkOut);
    if (guests) params = params.set('guests', guests);

    return this.http.get<RoomType[]>(`${this.apiUrl}/room-types/public/hotel/${hotelId}`, { params });
  }

  bookRoom(reservation: ReservationRequest, idempotencyKey?: string): Observable<any> {
    const options = idempotencyKey
      ? { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) }
      : {};
    return this.http.post(`${this.apiUrl}/reservations/book`, reservation, options);
  }

  getMyBookings(): Observable<ReservationSummary[]> {
    const params = new HttpParams().set('page', '0').set('size', '100');
    return this.http.get<ReservationSummaryPage>(`${this.apiUrl}/reservations/my-bookings/page`, { params })
      .pipe(map(result => result.content));
  }

  getReservation(reservationId: number): Observable<ReservationSummary> {
    return this.http.get<ReservationSummary>(`${this.apiUrl}/reservations/${reservationId}`);
  }

  getProfile(): Observable<UserContext> {
    return this.http.get<UserContext>(`${this.apiUrl}/users/me`);
  }

  searchLocations(keyword: string, size: number = 20): Observable<LocationSuggestion[]> {
    let params = new HttpParams()
      .set('keyword', keyword)
      .set('size', size.toString());
    return this.http.get<LocationSuggestion[]>(`${environment.apiUrl}/public/locations/search`, { params });
  }

  searchAutocomplete(keyword: string): Observable<LocationSuggestion[]> {
    return this.searchLocations(keyword, 15);
  }

  getSearchSuggestions(keyword: string, limit: number = 10, latitude?: number, longitude?: number, provinceId?: number): Observable<SearchSuggestionGroups> {
    let params = new HttpParams().set('keyword', keyword).set('limit', limit.toString());
    if (latitude !== undefined) params = params.set('latitude', latitude.toString());
    if (longitude !== undefined) params = params.set('longitude', longitude.toString());
    if (provinceId !== undefined) params = params.set('provinceId', provinceId.toString());
    return this.http.get<SearchSuggestionGroups>(`${environment.apiUrl}/public/search/suggestions`, { params });
  }

  getPopularDestinations(limit: number = 8, forceRefresh = false): Observable<LocationSuggestion[]> {
    const safeLimit = Math.min(Math.max(limit, 1), 8);
    const cached = this.popularDestinationsCache.get(safeLimit);
    if (!forceRefresh && cached && cached.expiresAt > Date.now()) return cached.response;
    this.popularDestinationsCache.delete(safeLimit);

    const params = new HttpParams().set('limit', safeLimit.toString());
    const request = this.http.get<LocationSuggestion[]>(
      `${environment.apiUrl}/public/popular-destinations`,
      { params }
    ).pipe(
      catchError(error => {
        if (this.popularDestinationsCache.get(safeLimit)?.response === request) {
          this.popularDestinationsCache.delete(safeLimit);
        }
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.popularDestinationsCache.set(safeLimit, {
      expiresAt: Date.now() + this.popularDestinationsTtlMs,
      response: request
    });
    return request;
  }

  invalidatePopularDestinations(limit?: number): void {
    if (limit === undefined) {
      this.popularDestinationsCache.clear();
      return;
    }
    this.popularDestinationsCache.delete(Math.min(Math.max(limit, 1), 8));
  }
}
