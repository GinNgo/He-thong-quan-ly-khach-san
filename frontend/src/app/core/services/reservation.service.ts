import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReservationDetail {
  id?: number;
  reservationId?: number;
  roomId: number;
  roomNumber?: string;
  priceAtBooking?: number;
}

export type PaymentLifecycleStatus = 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
export type RefundLifecycleStatus = 'REQUESTED' | 'PENDING_PROVIDER' | 'SUCCEEDED' | 'FAILED';

export interface PaymentLifecycleSummary {
  provider: string;
  amount: number;
  currency: 'VND';
  status: PaymentLifecycleStatus;
  expiresAt?: string;
  completedAt?: string;
  reconciliationRequired: boolean;
  failureCode?: string;
}

export interface RefundSummary {
  publicId: string;
  amount: number;
  currency: 'VND';
  provider: string;
  status: RefundLifecycleStatus;
  requestedAt: string;
  completedAt?: string;
  failureCode?: string;
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
  cancellationReasonCode?: string;
  cancellationReason?: string;
  cancelledAt?: string;
  details: ReservationDetail[];
  payment?: PaymentLifecycleSummary;
  refunds?: RefundSummary[];
}

@Injectable({
  providedIn: 'root',
})
export class ReservationService {
  private apiUrl = `${environment.apiUrl}/reservations`;

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

  checkIn(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/check-in`, {});
  }

  cancelOperational(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/cancel-operational`, {});
  }

  markNoShow(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/no-show`, {});
  }

  cancelMyReservation(id: number, cancellation: { reasonCode: string; reason?: string }, idempotencyKey?: string): Observable<Reservation> {
    const options = idempotencyKey
      ? { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) }
      : {};
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/cancel`, cancellation, options);
  }

  deleteReservation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
