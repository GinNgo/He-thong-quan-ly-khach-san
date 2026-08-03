import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReservationDetail {
  id?: number;
  reservationId?: number;
  roomId: number;
  roomNumber?: string;
  priceAtBooking?: number;
}

export interface ReservationEvent {
  id: number;
  eventType: string;
  reason: string;
  beforeState?: string;
  afterState?: string;
  actorType: string;
  occurredAt: string;
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
  events?: ReservationEvent[];
}

export interface ReservationPage {
  content: Reservation[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({
  providedIn: 'root'
})
export class ReservationService {
  private apiUrl = `${environment.apiUrl}/reservations`;


  constructor(private http: HttpClient) {}

  getAllReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(this.apiUrl);
  }

  searchReservations(options: {
    status?: string;
    query?: string;
    page?: number;
    size?: number;
  } = {}): Observable<ReservationPage> {
    let params = new HttpParams()
      .set('page', String(options.page ?? 0))
      .set('size', String(options.size ?? 20));
    if (options.status) params = params.set('status', options.status);
    if (options.query?.trim()) params = params.set('query', options.query.trim());
    return this.http.get<ReservationPage>(`${this.apiUrl}/page`, { params });
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

  checkIn(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/check-in`, {});
  }

  cancelOperational(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/cancel-operational`, {});
  }

  markNoShow(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/no-show`, {});
  }

  cancelMyReservation(id: number, idempotencyKey?: string): Observable<Reservation> {
    const options = idempotencyKey
      ? { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) }
      : {};
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/cancel`, {}, options);
  }

  deleteReservation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
