import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Hotel {
  id: number;
  name: string;
  addressLine: string;
  mainImage: string;
  starRating: number;
  latitude: number;
  longitude: number;
  distanceKm?: number;
  distanceText?: string;
  startingPrice?: number;
  approvalStatus?: string;
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
  basePrice: number;
  descriptionVi: string;
  descriptionEn: string;
  availableRooms?: number;
  nights?: number;
  totalPrice?: number;
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
}

export interface ReservationSummary {
  id: number;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
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

@Injectable({
  providedIn: 'root'
})
export class ClientApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
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
}
