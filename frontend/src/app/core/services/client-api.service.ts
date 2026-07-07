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
  code: string;
  nameVi: string;
  nameEn: string;
  maxGuest: number;
  basePrice: number;
  descriptionVi: string;
  descriptionEn: string;
}

export interface ReservationRequest {
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
  firstName: string;
  lastName: string;
  phone: string;
  paymentMethod: string;
}

@Injectable({
  providedIn: 'root'
})
export class ClientApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  searchHotels(city?: string, checkIn?: string, checkOut?: string, guests?: number): Observable<Hotel[]> {
    let params = new HttpParams();
    if (city) params = params.set('city', city);
    if (checkIn) params = params.set('checkIn', checkIn);
    if (checkOut) params = params.set('checkOut', checkOut);
    if (guests) params = params.set('guests', guests);

    return this.http.get<Hotel[]>(`${this.apiUrl}/hotels/public/search`, { params });
  }

  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.apiUrl}/hotels/public/${id}`);
  }

  getRoomTypesByHotel(hotelId: number): Observable<RoomType[]> {
    return this.http.get<RoomType[]>(`${this.apiUrl}/room-types/public/hotel/${hotelId}`);
  }

  bookRoom(reservation: ReservationRequest): Observable<any> {
    return this.http.post('http://localhost:8080/api/reservations/book', reservation);
  }
}
