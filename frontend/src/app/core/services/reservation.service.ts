import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReservationDetail {
  id?: number;
  reservationId?: number;
  roomId: number;
  roomNumber?: string;
  priceAtBooking?: number;
}

export interface Reservation {
  id?: number;
  userId: number;
  username?: string;
  userFullName?: string;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
  totalAmount?: number;
  status?: string;
  paymentMethod: string;
  specialRequests?: string;
  details: ReservationDetail[];
}

@Injectable({
  providedIn: 'root'
})
export class ReservationService {
  private apiUrl = 'http://localhost:8080/api/reservations';

  constructor(private http: HttpClient) {}

  getAllReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(this.apiUrl);
  }

  getReservationById(id: number): Observable<Reservation> {
    return this.http.get<Reservation>(`${this.apiUrl}/${id}`);
  }

  createReservation(reservation: Reservation): Observable<Reservation> {
    return this.http.post<Reservation>(this.apiUrl, reservation);
  }

  updateReservationStatus(id: number, status: string): Observable<Reservation> {
    return this.http.put<Reservation>(`${this.apiUrl}/${id}/status?status=${status}`, {});
  }

  deleteReservation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
