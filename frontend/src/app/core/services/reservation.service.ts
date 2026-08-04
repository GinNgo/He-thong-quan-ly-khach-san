import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FinancialMoney } from '../../shared/financial/financial.models';

export interface ReservationDetail {
  id?: number;
  reservationId?: number;
  roomId: number;
  roomNumber?: string;
  priceAtBooking?: number;
  roomTypeId?: number;
  roomTypeName?: string;
  quantity?: number;
  adults?: number;
  children?: number;
  subtotal?: number;
  assignedRoomIds?: number[];
  assignedRoomNumbers?: string[];
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

export type ReservationAmendmentStatus =
  | 'QUOTED'
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_PENDING'
  | 'APPLIED'
  | 'EXPIRED'
  | 'CANCELLED';

export interface ReservationAmendmentRequest {
  proposedRoomTypeId: number;
  proposedCheckInDate: string;
  proposedCheckOutDate: string;
  proposedQuantity: number;
  proposedAdults: number;
  proposedChildren: number;
}

export interface ReservationAmendmentStaySnapshot {
  roomTypeId: number;
  roomTypeName: string;
  checkInDate: string;
  checkOutDate: string;
  quantity: number;
  adults: number;
  children: number;
  totalAmount: FinancialMoney;
  depositRequired: FinancialMoney;
}

export interface ReservationAmendmentRoomTypeOption {
  id: number;
  name: string;
  maxAdults: number;
  maxChildren: number;
  maxGuests: number;
}

export interface ReservationAmendmentContext {
  reservationId: number;
  allowed: boolean;
  blockedReason?: string;
  cutoffAt: string;
  policyVersion: string;
  current: ReservationAmendmentStaySnapshot;
  roomTypeOptions: ReservationAmendmentRoomTypeOption[];
  paymentMethods: string[];
}

export interface ReservationAmendmentPaymentAttempt {
  attemptId: string;
  purpose: 'AMENDMENT_DELTA';
  status: 'CREATED' | 'PENDING' | 'PENDING_VERIFICATION' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'EXPIRED';
  expectedAmount: FinancialMoney;
  expiresAt: string;
  method: string;
  provider: string;
  uniqueTransferContent?: string | null;
}

export interface ReservationAmendmentQuote {
  publicId: string;
  reservationId: number;
  status: ReservationAmendmentStatus;
  policyVersion: string;
  original: ReservationAmendmentStaySnapshot;
  proposed: ReservationAmendmentStaySnapshot;
  priceDelta: FinancialMoney;
  preservedDiscount: FinancialMoney;
  expiresAt: string;
  cutoffAt: string;
  settlement: {
    type: 'NONE' | 'PAYMENT_REQUIRED' | 'PAYMENT_PENDING' | 'REFUND_PENDING';
    amount: FinancialMoney;
    paymentAttempt?: ReservationAmendmentPaymentAttempt | null;
    refundRequestPublicId?: string | null;
  };
  replayed: boolean;
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

  getAmendmentContext(id: number, staffMode = false): Observable<ReservationAmendmentContext> {
    return this.http.get<ReservationAmendmentContext>(`${this.amendmentBase(id, staffMode)}/amendment-context`);
  }

  createAmendmentQuote(
    id: number,
    request: ReservationAmendmentRequest,
    idempotencyKey: string,
    staffMode = false,
  ): Observable<ReservationAmendmentQuote> {
    return this.http.post<ReservationAmendmentQuote>(
      `${this.amendmentBase(id, staffMode)}/amendment-quotes`,
      request,
      { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) },
    );
  }

  getAmendmentQuote(
    id: number,
    quotePublicId: string,
    staffMode = false,
  ): Observable<ReservationAmendmentQuote> {
    return this.http.get<ReservationAmendmentQuote>(
      `${this.amendmentBase(id, staffMode)}/amendment-quotes/${encodeURIComponent(quotePublicId)}`,
    );
  }

  createAmendmentPaymentAttempt(
    id: number,
    quotePublicId: string,
    method: string,
    idempotencyKey: string,
    staffMode = false,
  ): Observable<ReservationAmendmentQuote> {
    return this.http.post<ReservationAmendmentQuote>(
      `${this.amendmentBase(id, staffMode)}/amendment-quotes/${encodeURIComponent(quotePublicId)}/payment-attempts`,
      { method },
      { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) },
    );
  }

  applyAmendmentQuote(
    id: number,
    quotePublicId: string,
    idempotencyKey: string,
    staffMode = false,
  ): Observable<ReservationAmendmentQuote> {
    return this.http.post<ReservationAmendmentQuote>(
      `${this.amendmentBase(id, staffMode)}/amendment-quotes/${encodeURIComponent(quotePublicId)}/apply`,
      null,
      { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) },
    );
  }

  private amendmentBase(id: number, staffMode: boolean): string {
    return staffMode
      ? `${environment.apiUrl}/management/reservations/${id}`
      : `${this.apiUrl}/${id}`;
  }
}
