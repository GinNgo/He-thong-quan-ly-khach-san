import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';
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
  propertyType?: string;
  provinceName?: string;
  wardName?: string;
  reviewScore?: number;
  reviewCount?: number;
  availableRoomCount?: number;
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
  private readonly popularDestinationsCache = new Map<number, Observable<LocationSuggestion[]>>();
  private hotelApiUrl = `${environment.apiUrl}/v1/hotels`;

  searchHotels(paramsObj: any): Observable<PagedResponse<Hotel>> {
    let params = new HttpParams();
    Object.keys(paramsObj).forEach(key => {
      if (paramsObj[key] !== null && paramsObj[key] !== undefined) {
        params = params.set(key, String(paramsObj[key]));
      }
    });

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

  submitPropertyClaim(propertyId: number, data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/properties/${propertyId}/claim`, data);
  }

  getRoomTypesByHotel(hotelId: number, checkIn?: string, checkOut?: string, guests?: number): Observable<RoomType[]> {
    let params = new HttpParams();
    if (checkIn) params = params.set('checkIn', checkIn);
    if (checkOut) params = params.set('checkOut', checkOut);
    if (guests) params = params.set('guests', guests);

    return this.http.get<RoomType[]>(`${this.apiUrl}/room-types/public/hotel/${hotelId}`, { params });
  }

  bookRoom(reservation: ReservationRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/reservations/book`, reservation);
  }

  getMyBookings(): Observable<ReservationSummary[]> {
    return this.http.get<ReservationSummary[]>(`${this.apiUrl}/reservations/my-bookings`);
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

  getSearchSuggestions(keyword: string, limit: number = 10, latitude?: number, longitude?: number): Observable<SearchSuggestionGroups> {
    let params = new HttpParams().set('keyword', keyword).set('limit', limit.toString());
    if (latitude !== undefined) params = params.set('latitude', latitude.toString());
    if (longitude !== undefined) params = params.set('longitude', longitude.toString());
    return this.http.get<SearchSuggestionGroups>(`${environment.apiUrl}/public/search/suggestions`, { params });
  }

  getPopularDestinations(limit: number = 8): Observable<LocationSuggestion[]> {
    const safeLimit = Math.min(Math.max(limit, 1), 12);
    const cached = this.popularDestinationsCache.get(safeLimit);
    if (cached) return cached;

    const params = new HttpParams().set('limit', safeLimit.toString());
    const request = this.http.get<LocationSuggestion[]>(
      `${environment.apiUrl}/public/popular-destinations`,
      { params }
    ).pipe(
      catchError(error => {
        this.popularDestinationsCache.delete(safeLimit);
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.popularDestinationsCache.set(safeLimit, request);
    return request;
  }
}
