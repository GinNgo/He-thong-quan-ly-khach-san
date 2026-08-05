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

export interface AvailablePhysicalRoom {
  id: number;
  hotelId: number;
  roomTypeId: number;
  roomTypeNameVi?: string;
  roomNumber: string;
  floor: number;
  status: string;
  housekeepingStatus: string;
  maintenanceStatus: string;
}

export interface AvailableRoomContext {
  reservationId: number;
  hotelId: number;
  roomTypeId: number;
  roomTypeName: string;
  checkInDate: string;
  checkOutDate: string;
  requiredQuantity: number;
  assignedRooms: AvailablePhysicalRoom[];
  assignedRoomIds: number[];
  candidates: AvailablePhysicalRoom[];
}

export interface RoomAssignmentMutationRequest {
  roomIds: number[];
  reason: string;
}

export interface CheckInReadinessIssue {
  code: 'INVALID_RESERVATION_STATUS' | 'ARRIVAL_WINDOW_NOT_OPEN' | 'STAY_WINDOW_CLOSED'
    | 'MISSING_ROOM_ASSIGNMENT' | 'ASSIGNMENT_PROPERTY_MISMATCH' | 'ROOM_NOT_READY'
    | 'CHECKED_IN_STATE_INCOMPLETE' | string;
  message: string;
}

export interface CheckInReadiness {
  reservationId: number;
  reservationStatus: string;
  ready: boolean;
  alreadyCheckedIn: boolean;
  evaluatedAt: string;
  scheduledArrivalAt: string;
  earliestCheckInAt: string;
  latestCheckInAt: string;
  zoneId: string;
  earlyWindowMinutes: number;
  policyVersion: string;
  requiredRoomCount: number;
  assignedRooms: AvailablePhysicalRoom[];
  blockers: CheckInReadinessIssue[];
}

export interface StaffBookingCustomerOption { id: number; fullName: string; username: string; maskedEmail: string; }
export interface StaffBookingRoomTypeOption { id: number; code: string; nameVi: string; nameEn: string; basePrice: number; maxAdults?: number; maxChildren?: number; maxGuests?: number; }
export interface StaffBookingContext { hotelId: number; hotelName: string; customers: StaffBookingCustomerOption[]; roomTypes: StaffBookingRoomTypeOption[]; paymentMethods: string[]; }
export interface StaffBookingQuoteRequest { hotelId: number; customerId: number; roomTypeId: number; checkInDate: string; checkOutDate: string; quantity: number; adults: number; children: number; paymentMethod: string; specialRequests?: string; }
export interface StaffBookingQuote extends StaffBookingQuoteRequest { quoteId: string; roomTypeName: string; availableRooms: number; basePrice: number; totalAmount: number; depositAmount: number; currency: 'VND'; expiresAt: string; status: string; replayed: boolean; }

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

  getCheckInReadiness(id: number): Observable<CheckInReadiness> {
    return this.http.get<CheckInReadiness>(`${this.apiUrl}/${id}/check-in-readiness`);
  }

  getStaffBookingContext(hotelId: number, customerQuery = ''): Observable<StaffBookingContext> {
    let params = new HttpParams().set('hotelId', String(hotelId));
    if (customerQuery.trim()) params = params.set('customerQuery', customerQuery.trim());
    return this.http.get<StaffBookingContext>(`${environment.apiUrl}/management/staff-bookings/context`, { params });
  }

  createStaffBookingQuote(request: StaffBookingQuoteRequest, idempotencyKey: string): Observable<StaffBookingQuote> {
    return this.http.post<StaffBookingQuote>(`${environment.apiUrl}/management/staff-bookings/quotes`, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  createStaffBooking(quoteId: string, idempotencyKey: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${environment.apiUrl}/management/staff-bookings`, { quoteId }, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  checkIn(id: number, idempotencyKey: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/check-in`, {}, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  cancelOperational(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/cancel-operational`, {});
  }

  markNoShow(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/no-show`, {});
  }

  getAvailableRoomContext(id: number): Observable<AvailableRoomContext> {
    return this.http.get<AvailableRoomContext>(`${this.apiUrl}/${id}/available-rooms/context`);
  }

  updateRoomAssignment(
    id: number,
    request: RoomAssignmentMutationRequest,
    idempotencyKey: string,
  ): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/room-assignment`, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  releaseRoomAssignment(id: number, reason: string, idempotencyKey: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.apiUrl}/${id}/room-assignment/release`, { reason }, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
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
