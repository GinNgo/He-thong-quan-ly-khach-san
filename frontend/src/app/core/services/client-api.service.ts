import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Hotel {
  id: number;
  name: string;
  description: string;
  address: string;
  city: string;
  country: string;
  starRating: number;
  mainImage: string;
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

  searchHotels(city?: string, checkIn?: string, checkOut?: string, guests?: number): Observable<Hotel[]> {
    let params = new HttpParams();
    if (city) params = params.set('city', city);
    if (checkIn) params = params.set('checkIn', checkIn);
    if (checkOut) params = params.set('checkOut', checkOut);
    if (guests) params = params.set('guests', guests);

    return this.http.get<Hotel[]>(`${this.hotelApiUrl}/public/search`, { params });
  }

  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.hotelApiUrl}/public/${id}`);
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
